package com.sentinelpay.backend.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import com.sentinelpay.backend.transaction.TransactionEvent;

public class PaymentPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final PaymentMessageCodec codec;
    private final String topic;

    public PaymentPublisher(KafkaTemplate<String, String> kafka, PaymentMessageCodec codec, String topic) {
        this.kafka = kafka;
        this.codec = codec;
        this.topic = topic;
    }

    public PublishReport publish(List<TransactionEvent> events) {
        if (events == null || events.isEmpty() || events.size() > 100) {
            throw new IllegalArgumentException("publish batch must contain 1 to 100 events");
        }
        // Validate the whole batch before sending the first event.
        List<String> payloads = events.stream().map(codec::encode).toList();
        var acknowledged = new ArrayList<String>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String currentId = null;
        try {
            for (int i = 0; i < events.size(); i++) {
                currentId = null;
                if (System.nanoTime() >= deadline) throw new java.util.concurrent.TimeoutException();
                currentId = events.get(i).id();
                var future = kafka.send(topic, currentId, payloads.get(i));
                future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                acknowledged.add(currentId);
            }
            return new PublishReport("ACKNOWLEDGED", acknowledged, null,
                    "Kafka acknowledged these IDs; database processing is asynchronous.");
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new PublishUnavailableException(new PublishReport("INCOMPLETE", acknowledged, currentId,
                    "Publishing did not finish. The uncertain ID may still arrive; query these IDs before retrying. "
                    + "A new simulation request creates new payments."), ex);
        }
    }
}
