package com.sentinelpay.backend.monitoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.sentinelpay.backend.pipeline.MinuteMetric;

/** Explainable synthetic-demo thresholds, not a trained model. */
public final class PaymentDetector {
    public enum State { NORMAL, WARNING, CRITICAL, INSUFFICIENT_DATA }
    public record Evaluation(String rule, State state, BigDecimal observed,
            BigDecimal warningThreshold, BigDecimal criticalThreshold, String explanation) {}

    public List<Evaluation> evaluate(MinuteMetric metric) {
        if (metric.totalCount() <= 0) throw new IllegalArgumentException("Positive sample count required");
        var total = BigDecimal.valueOf(metric.totalCount());
        var numerator = BigDecimal.valueOf(metric.failedCount()).multiply(BigDecimal.valueOf(100));
        var percent = numerator.divide(total, 3, RoundingMode.HALF_UP);
        boolean enough = metric.totalCount() >= 20;
        return List.of(new Evaluation("FAILURE_RATE", enough
                ? high(numerator, total.multiply(BigDecimal.valueOf(20)), total.multiply(BigDecimal.valueOf(50)))
                : State.INSUFFICIENT_DATA, percent, BigDecimal.valueOf(20), BigDecimal.valueOf(50),
                "Failed attempts as percent of all attempts; requires at least 20 payments."),
            new Evaluation("AVERAGE_LATENCY", enough
                ? high(metric.averageLatencyMs(), BigDecimal.valueOf(1000), BigDecimal.valueOf(2000))
                : State.INSUFFICIENT_DATA, metric.averageLatencyMs(), BigDecimal.valueOf(1000),
                BigDecimal.valueOf(2000), "Average latency in milliseconds; requires at least 20 payments."));
    }

    private State high(BigDecimal value, BigDecimal warning, BigDecimal critical) {
        return value.compareTo(critical) >= 0 ? State.CRITICAL
                : value.compareTo(warning) >= 0 ? State.WARNING : State.NORMAL;
    }

    public Evaluation volume(MinuteMetric current, List<MinuteMetric> history) {
        var counts = history.stream().filter(m -> m.currency().equals(current.currency()))
                .filter(m -> m.bucket().isBefore(current.bucket())
                    && !m.bucket().isBefore(current.bucket().minusSeconds(600)))
                .filter(m -> m.totalCount() >= 20).map(MinuteMetric::totalCount).sorted().toList();
        var observed = BigDecimal.valueOf(current.totalCount());
        if (counts.size() < 5) return new Evaluation("VOLUME_DROP", State.INSUFFICIENT_DATA,
                observed, null, null, "Requires five observed minutes with at least 20 payments in the preceding ten minutes for this currency.");
        int middle = counts.size()/2;
        var median = BigDecimal.valueOf(counts.get(middle));
        if (counts.size()%2 == 0) median = median.add(BigDecimal.valueOf(counts.get(middle-1)))
                .divide(BigDecimal.valueOf(2));
        var warning = median.multiply(new BigDecimal("0.5"));
        var critical = median.multiply(new BigDecimal("0.2"));
        var state = observed.compareTo(critical) <= 0 ? State.CRITICAL
                : observed.compareTo(warning) <= 0 ? State.WARNING : State.NORMAL;
        return new Evaluation("VOLUME_DROP", state, observed, warning, critical,
                "Volume at or below 50%/20% of preceding median " + median + " across "
                    + counts.size() + " qualifying minutes. Missing minutes are not assumed to be zero.");
    }
}
