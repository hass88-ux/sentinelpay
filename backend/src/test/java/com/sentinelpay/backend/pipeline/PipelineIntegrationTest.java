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
        var event = new PaymentSimulator(new Random(1)).generateTransaction();
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
}
