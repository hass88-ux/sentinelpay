package com.sentinelpay.backend.pipeline;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import com.sentinelpay.backend.monitoring.*;
import com.sentinelpay.backend.transaction.*;
import tools.jackson.databind.ObjectMapper;

/** Real Kafka -> PostgreSQL -> monitoring HTTP, with a persistent restart. */
public class MonitoringIntegrationTest {
    @Test
    void detectsCorrectsAndRetainsFindingsAfterRestart() throws Exception {
        var directory = Files.createTempDirectory(Path.of("target"),"part3-verify-").resolve("postgres");
        var bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES).minusSeconds(120);
        java.util.UUID caseId;
        try (var runtime = new TestPipeline(directory,0,true)) {
            var publisher = runtime.context.getBean(PaymentPublisher.class);
            var payments = runtime.context.getBean(PaymentStore.class);
            var ids = new ArrayList<String>();
            for (int minute=1;minute<=5;minute++) {
                var baseline = events(100,bucket.minusSeconds(minute*60),0,100);
                publisher.publish(baseline);
                baseline.forEach(e -> ids.add(e.id()));
            }
            var degraded = events(20,bucket,18,2500);
            publisher.publish(degraded);
            degraded.forEach(e -> ids.add(e.id()));
            await().atMost(Duration.ofSeconds(30)).until(() -> ids.stream().allMatch(id -> payments.find(id).isPresent()));
            var monitor = runtime.context.getBean(MonitoringStore.class);
            // This also proves the scheduled scan actually executes.
            await().atMost(Duration.ofSeconds(45)).until(() -> monitor.findings(false,100).size()==3);
            assertTrue(monitor.findings(false,100).stream().allMatch(f -> f.state().equals("CRITICAL")));
            var cases=runtime.context.getBean(com.sentinelpay.backend.incident.IncidentStore.class);
            assertEquals(1,cases.recent(false,100).size());
            caseId=cases.recent(false,100).getFirst().id();
            assertEquals(200,request(runtime,"POST","/api/monitoring/runs").statusCode());
            var response = request(runtime,"GET","/api/monitoring/findings");
            assertEquals(200,response.statusCode());
            assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(3,new ObjectMapper().readTree(response.body()).size());
            assertEquals(400,request(runtime,"GET","/api/monitoring/findings?limit=101").statusCode());
            assertEquals(400,request(runtime,"GET","/api/monitoring/findings?limit=abc").statusCode());
            var late = events(80,bucket,0,100);
            publisher.publish(late);
            await().atMost(Duration.ofSeconds(30)).until(() -> late.stream().allMatch(e -> payments.find(e.id()).isPresent()));
            assertEquals(200,request(runtime,"POST","/api/monitoring/runs").statusCode());
            assertTrue(monitor.findings(false,100).isEmpty());
            assertEquals(3,monitor.findings(true,100).size());
            assertTrue(monitor.findings(true,100).stream().allMatch(f -> f.state().equals("NORMAL")));
            assertEquals("CLEARED",cases.snapshot(caseId).orElseThrow().incident().state());
            assertEquals(200,request(runtime,"GET","/api/monitoring/status").statusCode());
        }
        try (var restarted = new TestPipeline(directory,0)) {
            var findings = restarted.context.getBean(MonitoringStore.class).findings(true,100);
            assertEquals(3,findings.size());
            assertTrue(findings.stream().allMatch(f -> f.state().equals("NORMAL")));
            assertEquals("CLEARED",restarted.context.getBean(com.sentinelpay.backend.incident.IncidentStore.class)
                    .snapshot(caseId).orElseThrow().incident().state());
        }
    }

    private List<TransactionEvent> events(int count, Instant time, int failures,long latency) {
        var result = new ArrayList<TransactionEvent>();
        for (int i=0;i<count;i++) result.add(new TransactionEvent(UUID.randomUUID().toString(),time,
                BigDecimal.ONE,Currency.getInstance("USD"),i<failures ? TransactionStatus.FAILED
                : TransactionStatus.SUCCESS,latency));
        return result;
    }
    private HttpResponse<String> request(TestPipeline runtime,String method,String path) throws Exception {
        var port=runtime.context.getEnvironment().getProperty("local.server.port");
        try (var client=HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                    .timeout(Duration.ofSeconds(30)).method(method,HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
