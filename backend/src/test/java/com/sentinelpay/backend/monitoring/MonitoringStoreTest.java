package com.sentinelpay.backend.monitoring;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import com.sentinelpay.backend.pipeline.PaymentStore;
import com.sentinelpay.backend.transaction.*;

class MonitoringStoreTest {
    @Test void completedBucketsAreIdempotentAndLatePaymentsClearFindings() throws Exception {
        try (var postgres = EmbeddedPostgres.builder().start()) {
            var source = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(source).load().migrate();
            var payments = new PaymentStore(source);
            var monitor = new MonitoringStore(source);
            var bucket = Instant.parse("2026-09-10T12:00:00Z");
            assertEquals("NO_DATA",monitor.evaluate(bucket).state());
            for (int i=0;i<20;i++) payments.save(event("failed"+i,bucket,TransactionStatus.FAILED,2500));
            assertEquals("NO_DATA",monitor.evaluate(bucket.plusSeconds(69)).state());
            assertEquals(2,monitor.evaluate(bucket.plusSeconds(70)).alertEvaluations());
            monitor.evaluate(bucket.plusSeconds(71));
            assertEquals(2,monitor.findings(true,100).size());
            var first = monitor.findings(true,100).getFirst().firstDetectedAt();
            for (int i=0;i<100;i++) payments.save(event("late"+i,bucket,TransactionStatus.SUCCESS,100));
            monitor.evaluate(bucket.plusSeconds(120));
            assertTrue(monitor.findings(false,100).isEmpty());
            assertTrue(monitor.findings(true,100).stream().allMatch(f -> f.state().equals("NORMAL")));
            assertEquals(first,new MonitoringStore(source).findings(true,100).getFirst().firstDetectedAt());
            assertThrows(IllegalArgumentException.class, () -> monitor.findings(false,101));
        }
    }
    private TransactionEvent event(String id, Instant time, TransactionStatus status,long latency) {
        return new TransactionEvent(id,time,BigDecimal.ONE,Currency.getInstance("USD"),status,latency);
    }
}
