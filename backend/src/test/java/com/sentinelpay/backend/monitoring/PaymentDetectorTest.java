package com.sentinelpay.backend.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.sentinelpay.backend.pipeline.MinuteMetric;

class PaymentDetectorTest {
    static MinuteMetric metric(long total, long failed, String latency) {
        return new MinuteMetric(Instant.parse("2026-09-10T12:00:00Z"), "USD", total,
                total-failed, failed, BigDecimal.TEN, new BigDecimal(latency), 5000);
    }
    @Test void inclusiveThresholdsAndSmallSamples() {
        var detector = new PaymentDetector();
        assertEquals(PaymentDetector.State.WARNING, detector.evaluate(metric(20,4,"1000")).get(0).state());
        assertEquals(PaymentDetector.State.CRITICAL, detector.evaluate(metric(20,10,"2000")).get(1).state());
        assertTrue(detector.evaluate(metric(19,19,"5000")).stream()
                .allMatch(e -> e.state() == PaymentDetector.State.INSUFFICIENT_DATA));
    }
    @Test void classificationDoesNotUseRoundedPercentage() {
        var result = new PaymentDetector().evaluate(metric(1_000_001,200_000,"999.999"));
        assertEquals(new BigDecimal("20.000"), result.get(0).observed());
        assertEquals(PaymentDetector.State.NORMAL, result.get(0).state());
        assertEquals(PaymentDetector.State.NORMAL, result.get(1).state());
    }
}
