package com.sentinelpay.backend.imports;

import com.sentinelpay.backend.pipeline.MinuteMetric;
import com.sentinelpay.backend.transaction.TransactionEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Every operation requires the authenticated owner; this store never reads the public demo tables. */
public final class PrivateUploadStore {
    public record Upload(UUID id, String filename, Instant createdAt, int transactionCount) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PrivateUploadStore(DataSource source) {
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setTimeout(20);
    }

    public Upload save(UUID owner, String filename, List<TransactionEvent> events) {
        Objects.requireNonNull(owner, "Authenticated owner required");
        if (filename == null || filename.isBlank() || filename.length() > 128
                || filename.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Filename must contain 1 to 128 characters without control characters");
        if (events == null || events.isEmpty() || events.size() > TransactionCsvReader.MAX_ROWS)
            throw new IllegalArgumentException("Upload must contain 1 to 5000 transactions");
        return transaction.execute(status -> {
            // Serialize quota checks for one owner, including requests handled by different Java instances.
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, owner.toString());
            Integer count = jdbc.queryForObject("SELECT count(*) FROM sentinelpay_private.upload WHERE owner_id = ?", Integer.class, owner);
            if (count != null && count >= 20) throw new IllegalArgumentException("Delete an older upload before adding more than 20 saved files");
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO sentinelpay_private.upload (id, owner_id, filename, transaction_count) VALUES (?, ?, ?, ?)", id, owner, filename, events.size());
            jdbc.batchUpdate("""
                    INSERT INTO sentinelpay_private.upload_event
                    (upload_id, owner_id, id, occurred_at, amount, currency, status, latency_ms)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, events, 500, (statement, event) -> {
                statement.setObject(1, id); statement.setObject(2, owner);
                statement.setString(3, event.id()); statement.setTimestamp(4, Timestamp.from(event.timestamp()));
                statement.setBigDecimal(5, event.amount()); statement.setString(6, event.currency().getCurrencyCode());
                statement.setString(7, event.status().name()); statement.setLong(8, event.latencyMs());
            });
            return find(owner, id).orElseThrow();
        });
    }

    public List<Upload> list(UUID owner) {
        Objects.requireNonNull(owner, "Authenticated owner required");
        return jdbc.query("SELECT id, filename, created_at, transaction_count FROM sentinelpay_private.upload WHERE owner_id = ? ORDER BY created_at DESC, id LIMIT 20",
                (r, n) -> new Upload(r.getObject("id", UUID.class), r.getString("filename"), r.getTimestamp("created_at").toInstant(), r.getInt("transaction_count")), owner);
    }

    public Optional<Upload> find(UUID owner, UUID id) {
        Objects.requireNonNull(owner, "Authenticated owner required");
        return jdbc.query("SELECT id, filename, created_at, transaction_count FROM sentinelpay_private.upload WHERE owner_id = ? AND id = ?",
                (r, n) -> new Upload(r.getObject("id", UUID.class), r.getString("filename"), r.getTimestamp("created_at").toInstant(), r.getInt("transaction_count")), owner, id).stream().findFirst();
    }

    public List<MinuteMetric> metrics(UUID owner, UUID id) {
        Objects.requireNonNull(owner, "Authenticated owner required");
        return jdbc.query("""
                SELECT date_trunc('minute', occurred_at) AS bucket, currency, count(*) AS total_count,
                  count(*) FILTER (WHERE status = 'SUCCESS') AS success_count,
                  count(*) FILTER (WHERE status = 'FAILED') AS failed_count,
                  sum(amount) AS total_amount, round(avg(latency_ms), 3) AS average_latency_ms,
                  max(latency_ms) AS max_latency_ms
                FROM sentinelpay_private.upload_event WHERE owner_id = ? AND upload_id = ?
                GROUP BY bucket, currency ORDER BY bucket, currency
                """, (r,n) -> new MinuteMetric(r.getTimestamp("bucket").toInstant(), r.getString("currency"),
                r.getLong("total_count"), r.getLong("success_count"), r.getLong("failed_count"),
                r.getBigDecimal("total_amount"), r.getBigDecimal("average_latency_ms"), r.getLong("max_latency_ms")), owner, id);
    }

    public boolean delete(UUID owner, UUID id) {
        Objects.requireNonNull(owner, "Authenticated owner required");
        return jdbc.update("DELETE FROM sentinelpay_private.upload WHERE owner_id = ? AND id = ?", owner, id) == 1;
    }

    /** Atomic fixed-window limits shared across application instances; one row per account/operation. */
    public boolean reserveRequest(UUID owner, String operation, int perMinute, int perDay) {
        return !jdbc.query("""
                INSERT INTO sentinelpay_private.request_limit AS r
                  (owner_id, operation, minute_start, minute_count, day_start, day_count)
                VALUES (?, ?, date_trunc('minute', now()), 1, date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC', 1)
                ON CONFLICT (owner_id, operation) DO UPDATE SET
                  minute_start = date_trunc('minute', now()),
                  minute_count = CASE WHEN r.minute_start = date_trunc('minute', now()) THEN r.minute_count + 1 ELSE 1 END,
                  day_start = date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC',
                  day_count = CASE WHEN r.day_start = date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' THEN r.day_count + 1 ELSE 1 END
                WHERE (r.minute_start < date_trunc('minute', now()) OR r.minute_count < ?)
                  AND (r.day_start < date_trunc('day', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' OR r.day_count < ?)
                RETURNING minute_count
                """, (r,n)->r.getInt(1), owner, operation, perMinute, perDay).isEmpty();
    }
}
