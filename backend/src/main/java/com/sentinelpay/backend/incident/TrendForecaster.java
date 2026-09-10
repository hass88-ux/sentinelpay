package com.sentinelpay.backend.incident;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import com.sentinelpay.backend.pipeline.MinuteMetric;

/** Five-point linear regression baseline; fit quality is not a probability. */
public final class TrendForecaster {
    public record Forecast(String signal, String state, Instant asOf, int horizonMinutes,
            Double currentValue, Double projectedValue, Double changePerMinute,
            Double fitRSquared, double warningThreshold, String explanation) {}

    public List<Forecast> forecast(String currency, Instant asOf, List<MinuteMetric> history) {
        var points = history.stream().filter(m -> m.currency().equals(currency))
                .filter(m -> !m.bucket().isAfter(asOf) && !m.bucket().isBefore(asOf.minusSeconds(240)))
                .sorted(Comparator.comparing(MinuteMetric::bucket)).toList();
        boolean enough = points.size() == 5;
        for (int i = 0; enough && i < 5; i++) {
            enough = points.get(i).totalCount() >= 20
                    && points.get(i).bucket().equals(asOf.minusSeconds((4-i)*60L));
        }
        if (!enough) return List.of(insufficient("FAILURE_RATE",asOf,20), insufficient("AVERAGE_LATENCY",asOf,1000));
        var failures = points.stream().mapToDouble(m -> BigDecimal.valueOf(m.failedCount())
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(m.totalCount()),12,RoundingMode.HALF_UP).doubleValue()).toArray();
        var latency = points.stream().mapToDouble(m -> m.averageLatencyMs().doubleValue()).toArray();
        return List.of(fit("FAILURE_RATE",asOf,failures,20,100),fit("AVERAGE_LATENCY",asOf,latency,1000,Long.MAX_VALUE));
    }

    private Forecast insufficient(String signal, Instant asOf, double threshold) {
        return new Forecast(signal,"INSUFFICIENT_DATA",asOf,3,null,null,null,null,threshold,
                "Requires five consecutive completed same-currency minutes with at least 20 payments each.");
    }

    private Forecast fit(String signal, Instant asOf, double[] values, double threshold, double maximum) {
        double mean = java.util.Arrays.stream(values).average().orElseThrow();
        double numerator = 0;
        for (int i=0;i<5;i++) numerator += (i-2)*(values[i]-mean);
        double slope = numerator/10;
        double residual = 0, variance = 0;
        for (int i=0;i<5;i++) {
            residual += Math.pow(values[i]-(mean+slope*(i-2)),2);
            variance += Math.pow(values[i]-mean,2);
        }
        double rSquared = variance == 0 ? 1 : Math.max(0,Math.min(1,1-residual/variance));
        double projected = Math.max(0,Math.min(maximum,mean+slope*5));
        String state = values[4] >= threshold ? "ALREADY_ELEVATED"
                : rSquared < 0.6 ? "UNSTABLE_TREND"
                : slope > 0 && projected >= threshold ? "THRESHOLD_RISK" : "NO_CROSSING_PROJECTED";
        return new Forecast(signal,state,asOf,3,values[4],projected,slope,rSquared,threshold,
                "Linear extrapolation three minutes beyond the latest of five observations. "
                + "R-squared measures historical fit, not confidence or incident probability; trend changes can invalidate the projection.");
    }
}
