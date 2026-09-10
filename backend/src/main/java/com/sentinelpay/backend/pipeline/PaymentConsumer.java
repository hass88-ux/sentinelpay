package com.sentinelpay.backend.pipeline;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

public class PaymentConsumer {
    private final PaymentStore store;
    private final PaymentMessageCodec codec;

    public PaymentConsumer(PaymentStore store, PaymentMessageCodec codec) {
        this.store = store;
        this.codec = codec;
    }

    @KafkaListener(id = "payment-storage", topics = "${pipeline.topic}", groupId = "${pipeline.group-id}",
            autoStartup = "${pipeline.consumer-enabled:true}")
    public void accept(ConsumerRecord<String, String> record) {
        store.save(codec.decode(record.key(), record.value()));
    }
}
