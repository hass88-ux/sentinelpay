package com.sentinelpay.backend.incident;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import com.sentinelpay.backend.monitoring.MonitoringStore;
import com.sentinelpay.backend.pipeline.PaymentStore;
import com.sentinelpay.backend.transaction.*;

class IncidentStoreTest {
    @Test void groupsRulesAndRetainsIdentityWhenLateDataClearsCase() throws Exception {
        try(var postgres=EmbeddedPostgres.builder().start()) {
            var source=postgres.getPostgresDatabase();
            Flyway.configure().dataSource(source).target("2").load().migrate();
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
            jdbc.update("""
                INSERT INTO monitoring_evaluation(bucket,currency,rule,state,sample_count,observed,
                    warning_threshold,critical_threshold,explanation,first_detected_at,evaluated_at)
                VALUES ('2020-01-01T00:00:00Z','EUR','FAILURE_RATE','CRITICAL',100,90,20,50,
                    'Historical failure rate','2020-01-01T00:02:00Z','2020-01-01T00:02:00Z')
                """);
            Flyway.configure().dataSource(source).load().migrate();
            var store=new IncidentStore(source);
            assertEquals("EUR",store.recent(false,100).getFirst().currency());
            jdbc.execute("TRUNCATE incident_case,monitoring_evaluation");
            var payments=new PaymentStore(source);
            var monitoring=new MonitoringStore(source);
            var bucket=Instant.parse("2026-09-10T12:00:00Z");
            for(int i=0;i<20;i++) payments.save(new TransactionEvent("bad"+i,bucket,BigDecimal.ONE,
                    Currency.getInstance("USD"),TransactionStatus.FAILED,2500));
            monitoring.evaluate(bucket.plusSeconds(70));
            var cases=store.recent(false,100);
            assertEquals(1,cases.size());
            var id=cases.getFirst().id();
            assertEquals("CRITICAL",cases.getFirst().severity());
            assertEquals(3,store.snapshot(id).orElseThrow().evidence().size());
            monitoring.evaluate(bucket.plusSeconds(71));
            assertEquals(id,store.recent(false,100).getFirst().id());
            for(int i=0;i<100;i++) payments.save(new TransactionEvent("late"+i,bucket,BigDecimal.ONE,
                    Currency.getInstance("USD"),TransactionStatus.SUCCESS,100));
            monitoring.evaluate(bucket.plusSeconds(120));
            assertTrue(store.recent(false,100).isEmpty());
            assertEquals("CLEARED",new IncidentStore(source).snapshot(id).orElseThrow().incident().state());
            assertEquals(1,store.recent(true,100).size());
            assertTrue(store.snapshot(java.util.UUID.randomUUID()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->store.recent(false,101));
        }
    }
}
