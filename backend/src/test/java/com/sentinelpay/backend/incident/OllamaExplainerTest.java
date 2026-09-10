package com.sentinelpay.backend.incident;

import java.math.BigDecimal;
import java.net.*;
import java.time.*;
import java.util.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.ObjectMapper;

class OllamaExplainerTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private HttpServer server;
    private URI endpoint;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/generate");
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private Investigator.Report report() {
        var now=Instant.now();
        var incident=new IncidentStore.Incident(UUID.randomUUID(),now,"USD","ALERT","CRITICAL",now,now);
        return new Investigator().investigate(new IncidentStore.Snapshot(incident,List.of(
                new IncidentStore.Evidence("FAILURE_RATE","CRITICAL",100,BigDecimal.valueOf(90),
                    BigDecimal.valueOf(20),BigDecimal.valueOf(50),"Failure percentage")),List.of()),now);
    }
    private void respond(int status,String body) {
        server.createContext("/api/generate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()) { output.write(bytes); }
        });
    }
    private String envelope(String rule) {
        return mapper.writeValueAsString(Map.of("done",true,"response",mapper.writeValueAsString(Map.of(
                "summary","Failures increased; the cause is unknown.",
                "hypotheses",List.of(Map.of("suggestion","Check processing errors.","evidenceRules",List.of(rule))),
                "nextChecks",List.of("Inspect failure codes.")))));
    }
    @Test void validatedSuggestionsRemainSeparateFromEvidence() {
        respond(200,envelope("FAILURE_RATE"));
        try(var client=new OllamaExplainer(true,"test-model",endpoint,Duration.ofSeconds(2),mapper)) {
            var report=report();
            var result=client.explain(report);
            assertEquals("AI_ASSISTED",result.mode());
            assertSame(report,result.report());
            assertEquals(List.of("FAILURE_RATE"),result.aiSuggestion().hypotheses().getFirst().evidenceRules());
        }
    }
    @Test void unknownEvidenceReferencesFallBack() {
        respond(200,envelope("INVENTED_RULE"));
        try(var client=new OllamaExplainer(true,"test-model",endpoint,Duration.ofSeconds(2),mapper)) {
            assertEquals("DETERMINISTIC",client.explain(report()).mode());
        }
    }
    @Test void disabledModeMakesNoRequest() {
        try(var client=new OllamaExplainer(false,"",endpoint,Duration.ofSeconds(2),mapper)) {
            assertTrue(client.explain(report()).notice().contains("DISABLED"));
        }
    }
    @Test void providerErrorsAndOversizedBodiesFallBack() {
        respond(503,"offline");
        try(var client=new OllamaExplainer(true,"test-model",endpoint,Duration.ofSeconds(2),mapper)) {
            assertTrue(client.explain(report()).notice().contains("PROVIDER_ERROR"));
        }
        server.removeContext("/api/generate");
        respond(200,"x".repeat(70000));
        try(var client=new OllamaExplainer(true,"test-model",endpoint,Duration.ofSeconds(2),mapper)) {
            assertEquals("DETERMINISTIC",client.explain(report()).mode());
        }
    }
    @Test void timeoutIsBoundedAndExternalEndpointsAreRejected() {
        server.createContext("/api/generate", exchange -> {
            try { Thread.sleep(400); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        try(var client=new OllamaExplainer(true,"test-model",endpoint,Duration.ofMillis(100),mapper)) {
            assertEquals("DETERMINISTIC",client.explain(report()).mode());
        }
        assertThrows(IllegalArgumentException.class,()->new OllamaExplainer(true,"test-model",
                URI.create("https://example.com/api/generate"),Duration.ofSeconds(2),mapper));
        assertThrows(IllegalArgumentException.class,()->new OllamaExplainer(true,"",endpoint,Duration.ofSeconds(2),mapper));
    }

    @Test void concurrentGenerationReturnsBusyWithoutWaiting() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        server.createContext("/api/generate",exchange -> {
            entered.countDown();
            try { release.await(2,java.util.concurrent.TimeUnit.SECONDS); }
            catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
            var bytes=envelope("FAILURE_RATE").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);
            try(var output=exchange.getResponseBody()) { output.write(bytes); }
        });
        try(var client=new OllamaExplainer(true,"test-model",endpoint,Duration.ofSeconds(3),mapper);
                var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->client.explain(report()));
            try {
                assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(client.explain(report()).notice().contains("BUSY"));
            } finally { release.countDown(); }
            assertEquals("AI_ASSISTED",first.get(3,java.util.concurrent.TimeUnit.SECONDS).mode());
        }
    }

    @Test void malformedAndIncompleteResponsesCannotReplaceReport() {
        respond(200,"{not json}");
        try(var client=new OllamaExplainer(true,"test-model",endpoint,Duration.ofSeconds(2),mapper)) {
            var report=report();
            assertSame(report,client.explain(report).report());
            assertNull(client.explain(report).aiSuggestion());
        }
        server.removeContext("/api/generate");
        respond(200,"{\"done\":false,\"response\":\"partial\"}");
        try(var client=new OllamaExplainer(true,"test-model",endpoint,Duration.ofSeconds(2),mapper)) {
            assertTrue(client.explain(report()).notice().contains("INVALID_RESPONSE"));
        }
    }
}
