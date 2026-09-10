package com.sentinelpay.backend.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import com.sentinelpay.backend.simulator.PaymentSimulator;
import tools.jackson.databind.json.JsonMapper;

class PaymentPublisherTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final PaymentPublisher publisher = new PaymentPublisher(kafka,
            new PaymentMessageCodec(JsonMapper.builder().findAndAddModules().build()), "payments");

    @Test
    void reportsAcknowledgedAndUncertainIdsWhenOnlyPartOfBatchSucceeds() {
        var simulator = new PaymentSimulator(new Random(1));
        var first = simulator.generateTransaction();
        var second = simulator.generateTransaction();
        var third = simulator.generateTransaction();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var failure = assertThrows(PublishUnavailableException.class, () -> publisher.publish(List.of(first, second, third)));
        assertEquals(List.of(first.id()), failure.report().acknowledgedIds());
        assertEquals(second.id(), failure.report().uncertainId());
        assertEquals("INCOMPLETE", failure.report().state());
        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void validatesEntireBatchBeforeSending() {
        var event = new PaymentSimulator(new Random(1)).generateTransaction();
        var invalid = new com.sentinelpay.backend.transaction.TransactionEvent("x".repeat(129), event.timestamp(),
                event.amount(), event.currency(), event.status(), event.latencyMs());
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(List.of(event, invalid)));
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(List.of()));
        verifyNoInteractions(kafka);
    }

    @Test
    void preservesInterruptionAndReportsUncertainDelivery() {
        var event = new PaymentSimulator(new Random(1)).generateTransaction();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        try {
            Thread.currentThread().interrupt();
            var failure = assertThrows(PublishUnavailableException.class, () -> publisher.publish(List.of(event)));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(event.id(), failure.report().uncertainId());
        } finally {
            Thread.interrupted();
        }
    }
}
