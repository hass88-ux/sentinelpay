package com.sentinelpay.backend.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import com.sentinelpay.backend.simulator.PaymentSimulator;
import java.util.Random;

class PaymentMessageCodecTest {
    private final PaymentMessageCodec codec = new PaymentMessageCodec(JsonMapper.builder().findAndAddModules().build());

    @Test
    void roundTripsAndChecksIdentity() {
        var event = new PaymentSimulator(new Random(1)).generateTransaction();
        assertEquals(event, codec.decode(event.id(), codec.encode(event)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("wrong-key", codec.encode(event)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "not json", "{\"schemaVersion\":2,\"event\":{}}",
            "{\"schemaVersion\":1,\"event\":{}}", "{\"schemaVersion\":4294967297,\"event\":{}}"})
    void rejectsBadMessages(String value) {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("key", value));
    }

    @Test
    void preservesExactDecimalAmounts() {
        var event = new com.sentinelpay.backend.transaction.TransactionEvent("precise", java.time.Instant.now(),
                new java.math.BigDecimal("1234567890.123456789"), java.util.Currency.getInstance("USD"),
                com.sentinelpay.backend.transaction.TransactionStatus.SUCCESS, 10);
        assertEquals(event.amount(), codec.decode(event.id(), codec.encode(event)).amount());
    }
}
