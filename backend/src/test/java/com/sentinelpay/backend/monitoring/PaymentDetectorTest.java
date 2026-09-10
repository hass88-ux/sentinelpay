package com.sentinelpay.backend.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.sentinelpay.backend.pipeline.MinuteMetric;

class PaymentDetectorTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"3,NORMAL", "4,WARNING", "9,WARNING", "10,CRITICAL", "20,CRITICAL"})
    void failureBoundaries(long failed, PaymentDetector.State expected) {
        assertEquals(expected,new PaymentDetector().evaluate(metric(20,failed,"100")).getFirst().state());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"999.999,NORMAL", "1000,WARNING", "1999.999,WARNING", "2000,CRITICAL"})
    void latencyBoundaries(String latency, PaymentDetector.State expected) {
        assertEquals(expected,new PaymentDetector().evaluate(metric(20,0,latency)).get(1).state());
    }

    @Test void medianResistsOutlierAndExcludesOldFutureAndSmallBuckets() {
        var current=metric(50,0,"100");
        var history=new java.util.ArrayList<MinuteMetric>();
        for (int i=1;i<=6;i++) history.add(new MinuteMetric(current.bucket().minusSeconds(i*60),"USD",
                i==6 ? 10000 : 100, i==6 ? 10000 : 100,0,BigDecimal.TEN,BigDecimal.TEN,10));
        var detector=new PaymentDetector();
        assertEquals(PaymentDetector.State.WARNING,detector.volume(current,history).state());
        assertEquals(0,new BigDecimal("50").compareTo(detector.volume(current,history).warningThreshold()));
        var unusable=java.util.List.of(
                new MinuteMetric(current.bucket().minusSeconds(601),"USD",100,100,0,BigDecimal.TEN,BigDecimal.TEN,10),
                new MinuteMetric(current.bucket().plusSeconds(60),"USD",100,100,0,BigDecimal.TEN,BigDecimal.TEN,10),
                new MinuteMetric(current.bucket().minusSeconds(60),"USD",19,19,0,BigDecimal.TEN,BigDecimal.TEN,10));
        assertEquals(PaymentDetector.State.INSUFFICIENT_DATA,detector.volume(current,unusable).state());
    }
    @Test void volumeUsesOnlyPastSameCurrencyAndNeedsHistory() {
        var detector = new PaymentDetector();
        var current = metric(20,0,"100");
        var history = java.util.stream.IntStream.rangeClosed(1,5).mapToObj(i ->
                new MinuteMetric(current.bucket().minusSeconds(i*60), "USD",100,100,0,
                    BigDecimal.TEN, BigDecimal.TEN,10)).toList();
        assertEquals(PaymentDetector.State.CRITICAL, detector.volume(current, history).state());
        assertEquals(PaymentDetector.State.INSUFFICIENT_DATA,
                detector.volume(current, history.subList(0,4)).state());
        assertEquals(PaymentDetector.State.INSUFFICIENT_DATA,
                detector.volume(current, java.util.List.of(current)).state());
        var foreign = history.stream().map(m -> new MinuteMetric(m.bucket(), "EUR",100,100,0,
                BigDecimal.TEN,BigDecimal.TEN,10)).toList();
        assertEquals(PaymentDetector.State.INSUFFICIENT_DATA, detector.volume(current, foreign).state());
    }
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
