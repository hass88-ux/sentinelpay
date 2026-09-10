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

    @Test
    void malformedRecordGoesToDeadLettersAndDoesNotBlockFollowingPayment() throws Exception {
        String badId = "bad-" + java.util.UUID.randomUUID();
        var consumerProps = new java.util.HashMap<String, Object>();
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, runtime.kafka.getBrokersAsString());
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (var deadLetters = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(consumerProps,
                new org.apache.kafka.common.serialization.StringDeserializer(), new org.apache.kafka.common.serialization.StringDeserializer())) {
            var partition = new org.apache.kafka.common.TopicPartition(properties.deadLetterTopic(), 0);
            deadLetters.assign(java.util.List.of(partition));
            deadLetters.seekToBeginning(java.util.List.of(partition));
            kafka.send(properties.topic(), 0, badId, "{invalid-json").get(10, TimeUnit.SECONDS);
            var marker = new PaymentSimulator(new Random(2)).generateTransaction();
            kafka.send(properties.topic(), 0, marker.id(), codec.encode(marker)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(30)).until(() -> store.find(marker.id()).isPresent());
            var found = new java.util.concurrent.atomic.AtomicReference<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                for (var record : deadLetters.poll(Duration.ofMillis(100))) {
                    if (badId.equals(record.key())) found.set(record);
                }
                return found.get() != null;
            });
            assertEquals("{invalid-json", found.get().value());
            assertTrue(found.get().headers().iterator().hasNext());
            assertTrue(store.find(badId).isEmpty());
        }
    }

    @Test
    void databaseConnectionFailureDoesNotCommitKafkaOffsetAndRecovers() throws Exception {
        var event = new PaymentSimulator(new Random(3)).generateTransaction();
        var observer = new org.springframework.jdbc.core.JdbcTemplate(runtime.postgres.getPostgresDatabase());
        try (var lock = runtime.postgres.getPostgresDatabase().getConnection();
                var admin = org.apache.kafka.clients.admin.Admin.create(java.util.Map.of(
                        "bootstrap.servers", runtime.kafka.getBrokersAsString()))) {
            lock.setAutoCommit(false);
            try (var statement = lock.createStatement()) {
                statement.execute("LOCK TABLE payment_event IN ACCESS EXCLUSIVE MODE");
            }
            var metadata = kafka.send(properties.topic(), 0, event.id(), codec.encode(event))
                    .get(10, TimeUnit.SECONDS).getRecordMetadata();
            var blockedPid = new java.util.concurrent.atomic.AtomicInteger();
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                var rows = observer.queryForList("SELECT pid FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                        + "AND query LIKE 'INSERT INTO payment_event%'", Integer.class);
                if (!rows.isEmpty()) blockedPid.set(rows.getFirst());
                return blockedPid.get() != 0;
            });
            observer.queryForObject("SELECT pg_terminate_backend(?)", Boolean.class, blockedPid.get());
            await().atMost(Duration.ofSeconds(20)).until(() -> observer.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                    + "AND query LIKE 'INSERT INTO payment_event%' AND pid <> ?", Integer.class, blockedPid.get()) > 0);
            var offsets = admin.listConsumerGroupOffsets(properties.groupId()).partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
            var committed = offsets.get(new org.apache.kafka.common.TopicPartition(properties.topic(), 0));
            assertTrue(committed == null || committed.offset() <= metadata.offset(), "uncommitted database event must remain replayable");
            lock.rollback();
            await().atMost(Duration.ofSeconds(30)).until(() -> store.find(event.id()).isPresent());
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                var offset = admin.listConsumerGroupOffsets(properties.groupId()).partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS).get(new org.apache.kafka.common.TopicPartition(properties.topic(), 0));
                return offset != null && offset.offset() > metadata.offset();
            });
        }
    }
}
