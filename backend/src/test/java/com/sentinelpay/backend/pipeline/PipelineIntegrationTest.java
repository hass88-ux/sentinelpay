package com.sentinelpay.backend.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.KafkaTemplate;
import com.sentinelpay.backend.simulator.PaymentSimulator;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PipelineIntegrationTest {
    private TestPipeline runtime;
    private PaymentStore store;
    private PaymentMessageCodec codec;
    private KafkaTemplate<String, String> kafka;
    private PipelineProperties properties;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void start() throws Exception {
        runtime = new TestPipeline();
        store = runtime.context.getBean(PaymentStore.class);
        codec = runtime.context.getBean(PaymentMessageCodec.class);
        kafka = (KafkaTemplate<String, String>) runtime.context.getBean("paymentKafkaTemplate");
        properties = runtime.context.getBean(PipelineProperties.class);
    }

    @AfterAll
    void stop() throws Exception {
        if (runtime != null) runtime.close();
    }

    @Test
    void deliversKafkaPaymentToPostgresAndDeduplicatesReplay() throws Exception {
        var generated = new PaymentSimulator(new Random(1)).generateTransaction();
        var event = new com.sentinelpay.backend.transaction.TransactionEvent(generated.id(),
                java.time.Instant.parse("2026-01-01T00:00:00Z"), generated.amount(), generated.currency(), generated.status(), generated.latencyMs());
        String json = codec.encode(event);
        kafka.send(properties.topic(), event.id(), json).get(10, TimeUnit.SECONDS);
        kafka.send(properties.topic(), event.id(), json).get(10, TimeUnit.SECONDS);
        // Same key/partition makes this marker prove the preceding replay has been consumed.
        var marker = new com.sentinelpay.backend.transaction.TransactionEvent(event.id() + "-marker",
                event.timestamp(), event.amount(), event.currency(), event.status(), event.latencyMs());
        int partition = kafka.send(properties.topic(), event.id(), json).get(10, TimeUnit.SECONDS).getRecordMetadata().partition();
        kafka.send(properties.topic(), partition, marker.id(), codec.encode(marker)).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).until(() -> store.find(marker.id()).isPresent());
        assertEquals(event.amount(), store.find(event.id()).orElseThrow().amount());
        var bucket = event.timestamp().truncatedTo(ChronoUnit.MINUTES);
        assertEquals(2, store.metrics(bucket, bucket.plusSeconds(60), 100).getFirst().totalCount());
    }

    private java.net.http.HttpResponse<String> request(String method, String path) throws Exception {
        String port = runtime.context.getEnvironment().getProperty("local.server.port");
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).method(method, java.net.http.HttpRequest.BodyPublishers.noBody()).build();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void httpPublishesAndEventuallyExposesStoredPaymentsAndMetrics() throws Exception {
        var response = request("POST", "/api/pipeline/simulations?count=3&scenario=DEGRADED&seed=42");
        assertEquals(202, response.statusCode(), response.body());
        var mapper = runtime.context.getBean(tools.jackson.databind.ObjectMapper.class);
        var report = mapper.readTree(response.body());
        assertEquals("ACKNOWLEDGED", report.get("state").asText());
        assertEquals(3, report.get("acknowledgedIds").size());
        for (var id : report.get("acknowledgedIds")) {
            await().atMost(Duration.ofSeconds(30)).until(() -> store.find(id.asText()).isPresent());
            assertEquals(200, request("GET", "/api/pipeline/transactions/" + id.asText()).statusCode());
        }
        assertEquals(404, request("GET", "/api/pipeline/transactions/missing-payment").statusCode());
        assertEquals(1, mapper.readTree(request("GET", "/api/pipeline/transactions?limit=1").body()).size());
        var metrics = request("GET", "/api/pipeline/metrics");
        assertEquals(200, metrics.statusCode(), metrics.body());
        assertFalse(mapper.readTree(metrics.body()).isEmpty());
    }

    @Test
    void httpRejectsBadCountsLimitsAndTimeRanges() throws Exception {
        for (String query : new String[] {"count=0", "count=101", "count=abc", "scenario=bad", "seed=abc"}) {
            var response = request("POST", "/api/pipeline/simulations?" + query);
            assertEquals(400, response.statusCode(), response.body());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/problem+json"));
        }
        assertEquals(400, request("GET", "/api/pipeline/transactions?limit=101").statusCode());
        for (String query : new String[] {"from=2026-01-01T00:00:00Z", "from=bad&to=bad",
                "from=2026-01-01T00:00:01Z&to=2026-01-01T00:01:00Z",
                "from=2026-01-01T00:00:00Z&to=2026-01-03T00:00:00Z"}) {
            assertEquals(400, request("GET", "/api/pipeline/metrics?" + query).statusCode(), query);
        }
    }
}
