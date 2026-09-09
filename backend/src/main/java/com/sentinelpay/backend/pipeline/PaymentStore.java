package com.sentinelpay.backend.pipeline;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sentinelpay.backend.transaction.TransactionEvent;
import com.sentinelpay.backend.transaction.TransactionStatus;

/** PostgreSQL is the source of truth. Raw insert and rollup update share one transaction. */
public class PaymentStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PaymentStore(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(10);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setTimeout(15);
    }

    public boolean save(TransactionEvent event) {
        if (event.id().length() > 128) {
            throw new IllegalArgumentException("event id must not exceed 128 characters");
        }
        // PostgreSQL stores microseconds. Normalize once before writing and comparing duplicates.
        Instant time = event.timestamp().truncatedTo(ChronoUnit.MICROS);
        return Boolean.TRUE.equals(transaction.execute(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO payment_event (id, occurred_at, amount, currency, status, latency_ms)
                    VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                    """, event.id(), Timestamp.from(time), event.amount(), event.currency().getCurrencyCode(),
                    event.status().name(), event.latencyMs());
            if (inserted == 0) {
                TransactionEvent existing = find(event.id()).orElseThrow();
                if (!existing.timestamp().equals(time) || existing.amount().compareTo(event.amount()) != 0
                        || !existing.currency().equals(event.currency()) || existing.status() != event.status()
                        || existing.latencyMs() != event.latencyMs()) {
                    throw new IllegalArgumentException("event id already exists with different payment data");
                }
                return false;
            }
            long success = event.status() == TransactionStatus.SUCCESS ? 1 : 0;
            jdbc.update("""
                    INSERT INTO payment_minute
                    (bucket, currency, total_count, success_count, failed_count, total_amount, total_latency_ms, max_latency_ms)
                    VALUES (?, ?, 1, ?, ?, ?, ?, ?)
                    ON CONFLICT (bucket, currency) DO UPDATE SET
                    total_count = payment_minute.total_count + 1,
                    success_count = payment_minute.success_count + EXCLUDED.success_count,
                    failed_count = payment_minute.failed_count + EXCLUDED.failed_count,
                    total_amount = payment_minute.total_amount + EXCLUDED.total_amount,
                    total_latency_ms = payment_minute.total_latency_ms + EXCLUDED.total_latency_ms,
                    max_latency_ms = GREATEST(payment_minute.max_latency_ms, EXCLUDED.max_latency_ms)
                    """, Timestamp.from(time.truncatedTo(ChronoUnit.MINUTES)), event.currency().getCurrencyCode(),
                    success, 1 - success, event.amount(), BigDecimal.valueOf(event.latencyMs()), event.latencyMs());
            return true;
        }));
    }

    public Optional<TransactionEvent> find(String id) {
        return jdbc.query("SELECT * FROM payment_event WHERE id = ?", this::eventRow, id).stream().findFirst();
    }

    public List<TransactionEvent> recent(int limit) {
        validateLimit(limit);
        return jdbc.query("SELECT * FROM payment_event ORDER BY occurred_at DESC, id LIMIT ?", this::eventRow, limit);
    }

    public List<MinuteMetric> metrics(Instant from, Instant to, int limit) {
        validateLimit(limit);
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }
        return jdbc.query("""
                SELECT bucket, currency, total_count, success_count, failed_count, total_amount,
                round(total_latency_ms / total_count, 3) AS average_latency_ms, max_latency_ms
                FROM payment_minute WHERE bucket >= ? AND bucket < ? ORDER BY bucket DESC, currency LIMIT ?
                """, (row, index) -> new MinuteMetric(row.getTimestamp("bucket").toInstant(), row.getString("currency"),
                row.getLong("total_count"), row.getLong("success_count"), row.getLong("failed_count"),
                row.getBigDecimal("total_amount"), row.getBigDecimal("average_latency_ms"), row.getLong("max_latency_ms")),
                Timestamp.from(from), Timestamp.from(to), limit);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }

    private TransactionEvent eventRow(ResultSet row, int index) throws SQLException {
        return new TransactionEvent(row.getString("id"), row.getTimestamp("occurred_at").toInstant(),
                row.getBigDecimal("amount"), Currency.getInstance(row.getString("currency")),
                TransactionStatus.valueOf(row.getString("status")), row.getLong("latency_ms"));
    }
}
