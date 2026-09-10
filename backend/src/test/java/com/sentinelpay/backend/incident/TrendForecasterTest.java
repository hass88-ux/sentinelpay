package com.sentinelpay.backend.incident;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.sentinelpay.backend.pipeline.MinuteMetric;

class TrendForecasterTest {
    private final Instant end=Instant.parse("2026-09-10T12:00:00Z");
    private List<MinuteMetric> points(long... latencies) {
        var result=new ArrayList<MinuteMetric>();
        for(int i=0;i<latencies.length;i++) result.add(new MinuteMetric(end.minusSeconds((4-i)*60),"USD",
                100,100-i*4,i*4,BigDecimal.TEN,BigDecimal.valueOf(latencies[i]),latencies[i]));
        return result;
    }
    @Test void predictsCrossingWithoutLookingIntoFuture() {
        var history=points(200,350,500,650,800);
        history.add(new MinuteMetric(end.plusSeconds(60),"USD",100,0,100,BigDecimal.TEN,BigDecimal.ZERO,0));
        var result=new TrendForecaster().forecast("USD",end,history);
        assertEquals("THRESHOLD_RISK",result.get(0).state());
        assertEquals(28,result.get(0).projectedValue(),0.00001);
        assertEquals(1250,result.get(1).projectedValue(),0.00001);
        assertEquals(1,result.get(1).fitRSquared(),0.00001);
    }
    @Test void refusesGapsOtherCurrenciesAndSmallSamples() {
        var detector=new TrendForecaster();
        assertEquals("INSUFFICIENT_DATA",detector.forecast("EUR",end,points(100,200,300,400,500)).getFirst().state());
        assertEquals("INSUFFICIENT_DATA",detector.forecast("USD",end,points(100,200,300,400)).getFirst().state());
        var small=points(100,200,300,400,500);
        small.set(2,new MinuteMetric(end.minusSeconds(120),"USD",19,19,0,BigDecimal.TEN,BigDecimal.TEN,10));
        assertEquals("INSUFFICIENT_DATA",detector.forecast("USD",end,small).getFirst().state());
    }
    @Test void distinguishesFlatNoisyAndAlreadyElevatedTrends() {
        var detector=new TrendForecaster();
        assertEquals("NO_CROSSING_PROJECTED",detector.forecast("USD",end,points(100,100,100,100,100)).get(1).state());
        assertEquals("UNSTABLE_TREND",detector.forecast("USD",end,points(900,100,800,100,900)).get(1).state());
        assertEquals("ALREADY_ELEVATED",detector.forecast("USD",end,points(200,400,600,800,1000)).get(1).state());
        assertEquals(0,detector.forecast("USD",end,points(900,700,500,300,100)).get(1).projectedValue());
    }
}
