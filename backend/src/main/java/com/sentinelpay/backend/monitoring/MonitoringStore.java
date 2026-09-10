package com.sentinelpay.backend.monitoring;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sentinelpay.backend.pipeline.MinuteMetric;

/** Re-evaluation replaces a bucket's state while retaining its first alert time. */
public final class MonitoringStore {
    public record Run(String state, Instant evaluatedAt, Instant exclusiveCutoff,
            int observedMinutes, int alertEvaluations, int insufficientEvaluations) {}
    public record Finding(Instant bucket, String currency, String rule, String state,
            long sampleCount, BigDecimal observed, BigDecimal warningThreshold,
            BigDecimal criticalThreshold, String explanation, Instant firstDetectedAt, Instant evaluatedAt) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PaymentDetector detector = new PaymentDetector();

    public MonitoringStore(DataSource source) {
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setTimeout(20);
    }

    public Run evaluate(Instant now) {
        var cutoff = now.minusSeconds(10).truncatedTo(ChronoUnit.MINUTES);
        return transaction.execute(status -> {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(736281904)", Boolean.class)))
                return new Run("BUSY",now,cutoff,0,0,0);
            // One query gives all rules the same committed metric snapshot.
            var history = jdbc.query("""
                    SELECT *, ROUND(total_latency_ms/total_count,3) AS average_latency_ms
                    FROM payment_minute WHERE bucket >= ? AND bucket < ? ORDER BY bucket,currency
                    """, (rs,row) -> new MinuteMetric(rs.getTimestamp("bucket").toInstant(),
                        rs.getString("currency"),rs.getLong("total_count"),rs.getLong("success_count"),
                        rs.getLong("failed_count"),rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("average_latency_ms"),rs.getLong("max_latency_ms")),
                    Timestamp.from(cutoff.minusSeconds(2400)),Timestamp.from(cutoff));
            int minutes=0, alerts=0, insufficient=0;
            for (var metric : history) {
                if (metric.bucket().isBefore(cutoff.minusSeconds(1800))) continue;
                minutes++;
                var evaluations = new ArrayList<>(detector.evaluate(metric));
                evaluations.add(detector.volume(metric,history));
                for (var evaluation : evaluations) {
                    boolean alert = evaluation.state() == PaymentDetector.State.WARNING
                            || evaluation.state() == PaymentDetector.State.CRITICAL;
                    if (alert) alerts++;
                    if (evaluation.state() == PaymentDetector.State.INSUFFICIENT_DATA) insufficient++;
                    jdbc.update("""
                        INSERT INTO monitoring_evaluation(bucket,currency,rule,state,sample_count,observed,
                            warning_threshold,critical_threshold,explanation,first_detected_at,evaluated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT(bucket,currency,rule) DO UPDATE SET
                            state=EXCLUDED.state,sample_count=EXCLUDED.sample_count,observed=EXCLUDED.observed,
                            warning_threshold=EXCLUDED.warning_threshold,critical_threshold=EXCLUDED.critical_threshold,
                            explanation=EXCLUDED.explanation,evaluated_at=EXCLUDED.evaluated_at,
                            first_detected_at=COALESCE(monitoring_evaluation.first_detected_at,EXCLUDED.first_detected_at)
                        """,Timestamp.from(metric.bucket()),metric.currency(),evaluation.rule(),evaluation.state().name(),
                        metric.totalCount(),evaluation.observed(),evaluation.warningThreshold(),evaluation.criticalThreshold(),
                        evaluation.explanation(),alert ? Timestamp.from(now) : null,Timestamp.from(now));
                }
            }
            return new Run(minutes == 0 ? "NO_DATA" : "COMPLETED",now,cutoff,minutes,alerts,insufficient);
        });
    }

    public List<Finding> findings(boolean includeResolved, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return jdbc.query("""
                SELECT * FROM monitoring_evaluation WHERE first_detected_at IS NOT NULL
                AND (? OR state IN ('WARNING','CRITICAL')) ORDER BY bucket DESC,currency,rule LIMIT ?
                """, (rs,row) -> new Finding(rs.getTimestamp("bucket").toInstant(),rs.getString("currency"),
                    rs.getString("rule"),rs.getString("state"),rs.getLong("sample_count"),rs.getBigDecimal("observed"),
                    rs.getBigDecimal("warning_threshold"),rs.getBigDecimal("critical_threshold"),rs.getString("explanation"),
                    rs.getTimestamp("first_detected_at").toInstant(),rs.getTimestamp("evaluated_at").toInstant()),
                includeResolved,limit);
    }
}
