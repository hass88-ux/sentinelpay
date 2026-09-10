package com.sentinelpay.backend.pipeline;

import java.time.Instant;
import tools.jackson.databind.ObjectMapper;
import com.sentinelpay.backend.transaction.TransactionEvent;

public class PaymentMessageCodec {
    private final ObjectMapper mapper;

    public PaymentMessageCodec(ObjectMapper mapper) {
        this.mapper = mapper.rebuild().enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    }

    public String encode(TransactionEvent event) {
        validate(event);
        return mapper.writeValueAsString(new PaymentMessage(1, event));
    }

    public TransactionEvent decode(String key, String json) {
        try {
            var root = mapper.readTree(json);
            if (root == null || !root.hasNonNull("schemaVersion") || !root.get("schemaVersion").isIntegralNumber()
                    || !root.get("schemaVersion").canConvertToInt()
                    || root.get("schemaVersion").intValue() != 1 || !root.hasNonNull("event")) {
                throw new IllegalArgumentException("a version 1 payment event is required");
            }
            var node = root.get("event");
            for (String field : new String[] {"id", "timestamp", "amount", "currency", "status", "latencyMs"}) {
                if (!node.hasNonNull(field)) throw new IllegalArgumentException("payment field is missing: " + field);
            }
            if (!node.get("latencyMs").isIntegralNumber() || !node.get("latencyMs").canConvertToLong()
                    || !node.get("amount").isNumber()) {
                throw new IllegalArgumentException("amount and latency must be valid numeric values");
            }
            var event = mapper.treeToValue(node, TransactionEvent.class);
            validate(event);
            if (!event.id().equals(key)) throw new IllegalArgumentException("Kafka key must match payment id");
            return event;
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalArgumentException("invalid payment JSON", ex);
        }
    }

    private void validate(TransactionEvent event) {
        if (event == null || event.id().length() > 128
                || event.timestamp().isBefore(Instant.parse("1970-01-01T00:00:00Z"))
                || !event.timestamp().isBefore(Instant.parse("2200-01-01T00:00:00Z"))) {
            throw new IllegalArgumentException("payment needs an id of at most 128 characters and a timestamp from 1970 to before 2200");
        }
    }
}
