package com.sentinelpay.backend.incident;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvestigatorTest {
    @Test void hypothesesCiteObservedRulesAndDoNotClaimRootCause() {
        var now=Instant.parse("2026-09-10T12:00:00Z");
        var incident=new IncidentStore.Incident(UUID.randomUUID(),now,"USD","ALERT","CRITICAL",now,now);
        var evidence=new IncidentStore.Evidence("FAILURE_RATE","CRITICAL",100,BigDecimal.valueOf(90),
                BigDecimal.valueOf(20),BigDecimal.valueOf(50),"Failure percentage");
        var report=new Investigator().investigate(new IncidentStore.Snapshot(incident,List.of(evidence),List.of()),now);
        assertEquals(List.of("FAILURE_RATE"),report.hypotheses().getFirst().evidenceRules());
        assertEquals("INSUFFICIENT_DATA",report.forecasts().getFirst().state());
        assertTrue(report.limitations().stream().anyMatch(s -> s.contains("Root cause is unknown")));
        assertEquals(evidence,report.evidence().getFirst());
    }
}
