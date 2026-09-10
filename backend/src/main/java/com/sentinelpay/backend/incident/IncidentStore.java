package com.sentinelpay.backend.incident;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.sentinelpay.backend.pipeline.MinuteMetric;

public final class IncidentStore {
    public record Incident(UUID id, Instant bucket, String currency, String state, String severity,
            Instant firstDetectedAt, Instant evaluatedAt) {}
    public record Evidence(String rule, String state, long sampleCount, BigDecimal observed,
            BigDecimal warningThreshold, BigDecimal criticalThreshold, String explanation) {}
    public record Snapshot(Incident incident, List<Evidence> evidence, List<MinuteMetric> history) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate read;

    public IncidentStore(DataSource source) {
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        read = new TransactionTemplate(new DataSourceTransactionManager(source));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(15);
    }

    public List<Incident> recent(boolean includeCleared, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return jdbc.query("SELECT * FROM incident_case WHERE (? OR state <> 'CLEARED') ORDER BY bucket DESC,id LIMIT ?",
                (rs,row) -> incident(rs),includeCleared,limit);
    }

    public Optional<Snapshot> snapshot(UUID id) {
        return read.execute(status -> {
            var cases = jdbc.query("SELECT * FROM incident_case WHERE id=?",(rs,row) -> incident(rs),id);
            if (cases.isEmpty()) return Optional.empty();
            var incident = cases.getFirst();
            var evidence = jdbc.query("""
                    SELECT * FROM monitoring_evaluation WHERE bucket=? AND currency=? ORDER BY rule
                    """,(rs,row) -> new Evidence(rs.getString("rule"),rs.getString("state"),rs.getLong("sample_count"),
                        rs.getBigDecimal("observed"),rs.getBigDecimal("warning_threshold"),
                        rs.getBigDecimal("critical_threshold"),rs.getString("explanation")),
                    Timestamp.from(incident.bucket()),incident.currency());
            return Optional.of(new Snapshot(incident,evidence,history(incident.currency(),incident.bucket())));
        });
    }

    public List<MinuteMetric> history(String currency, Instant asOf) {
        return jdbc.query("""
                SELECT *,ROUND(total_latency_ms/total_count,3) AS average_latency_ms FROM payment_minute
                WHERE currency=? AND bucket>=? AND bucket<=? ORDER BY bucket
                """,(rs,row) -> new MinuteMetric(rs.getTimestamp("bucket").toInstant(),rs.getString("currency"),
                    rs.getLong("total_count"),rs.getLong("success_count"),rs.getLong("failed_count"),
                    rs.getBigDecimal("total_amount"),rs.getBigDecimal("average_latency_ms"),rs.getLong("max_latency_ms")),
                currency,Timestamp.from(asOf.minusSeconds(240)),Timestamp.from(asOf));
    }

    private Incident incident(ResultSet rs) throws SQLException {
        return new Incident(rs.getObject("id",UUID.class),rs.getTimestamp("bucket").toInstant(),rs.getString("currency"),
                rs.getString("state"),rs.getString("severity"),rs.getTimestamp("first_detected_at").toInstant(),
                rs.getTimestamp("evaluated_at").toInstant());
    }
}
