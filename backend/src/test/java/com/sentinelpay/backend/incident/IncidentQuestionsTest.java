package com.sentinelpay.backend.incident;

import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.sentinelpay.backend.pipeline.MinuteMetric;
import tools.jackson.databind.ObjectMapper;

class IncidentQuestionsTest {
    private IncidentStore.Snapshot snapshot() {
        var bucket = Instant.parse("2026-01-01T12:00:00Z");
        var incident = new IncidentStore.Incident(UUID.randomUUID(),bucket,"USD","ALERT","CRITICAL",bucket,bucket);
        var evidence = new IncidentStore.Evidence("AVERAGE_LATENCY","CRITICAL",100,BigDecimal.valueOf(2500),
                BigDecimal.valueOf(1000),BigDecimal.valueOf(2000),"Average latency in milliseconds.");
        var history = List.of(metric(bucket.minusSeconds(120),200),metric(bucket.minusSeconds(60),800),metric(bucket,2500));
        return new IncidentStore.Snapshot(incident,List.of(evidence),history);
    }
    private MinuteMetric metric(Instant bucket,long latency) {
        return new MinuteMetric(bucket,"USD",100,100,0,BigDecimal.ONE,BigDecimal.valueOf(latency),latency);
    }
    private OllamaExplainer disabled() {
        return new OllamaExplainer(false,"",URI.create("http://127.0.0.1:11434/api/generate"),Duration.ofSeconds(2),new ObjectMapper());
    }
    @Test void explainsFlagsWithEvidenceWithoutAModel() {
        try(var model=disabled()) {
            var answer=new IncidentQuestions().answer(snapshot(),"Why was this flagged?",model);
            assertEquals("DETERMINISTIC",answer.mode());
            assertEquals(List.of("AVERAGE_LATENCY"),answer.evidenceRules());
            assertTrue(answer.answer().contains("2500"));
            assertTrue(answer.answer().contains("CRITICAL"));
        }
    }
    @Test void beforeComparisonExcludesIncidentAndUnknownQuestionsStayHonest() {
        try(var model=disabled()) {
            var questions=new IncidentQuestions();
            var answer=questions.answer(snapshot(),"What changed before the spike?",model);
            assertTrue(answer.answer().contains("200 to 800"));
            assertFalse(answer.answer().contains("2500"));
            assertTrue(questions.answer(snapshot(),"Which processor caused this?",model).answer().contains("unknown"));
            assertTrue(questions.answer(snapshot(),"Write a poem",model).answer().contains("may not answer"));
        }
    }
    @Test void rejectsMissingBlankAndOversizedQuestions() {
        assertThrows(IllegalArgumentException.class,()->IncidentQuestions.validate(null));
        assertThrows(IllegalArgumentException.class,()->IncidentQuestions.validate("   "));
        assertThrows(IllegalArgumentException.class,()->IncidentQuestions.validate("x".repeat(501)));
        assertEquals("Why?",IncidentQuestions.validate(" Why? "));
    }
}
