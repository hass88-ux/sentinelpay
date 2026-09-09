package com.sentinelpay.backend.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.EnumSet;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sentinelpay.backend.transaction.TransactionEvent;
import com.sentinelpay.backend.transaction.TransactionStatus;

class PaymentSimulatorTest {
    @Test
    void generatesValidPaymentDetailsWithinDemoBounds() {
        PaymentSimulator simulator = new PaymentSimulator(new Random(42));
        EnumSet<TransactionStatus> observedStatuses = EnumSet.noneOf(TransactionStatus.class);

        for (int i = 0; i < 1_000; i++) {
            TransactionEvent event = simulator.generateTransaction();
            assertEquals(event.id(), UUID.fromString(event.id()).toString());
            assertNotNull(event.timestamp());
            assertEquals(Currency.getInstance("USD"), event.currency());
            assertEquals(2, event.amount().scale());
            assertTrue(event.amount().compareTo(new BigDecimal("0.01")) >= 0);
            assertTrue(event.amount().compareTo(new BigDecimal("500.00")) <= 0);
            assertTrue(event.latencyMs() >= 10 && event.latencyMs() <= 500);
            observedStatuses.add(event.status());
        }

        assertEquals(EnumSet.allOf(TransactionStatus.class), observedStatuses);
    }

    @Test
    void repeatsSimulatedFieldsWithTheSameSeed() {
        PaymentSimulator first = new PaymentSimulator(new Random(123));
        PaymentSimulator second = new PaymentSimulator(new Random(123));

        for (int i = 0; i < 100; i++) {
            TransactionEvent firstEvent = first.generateTransaction();
            TransactionEvent secondEvent = second.generateTransaction();
            assertEquals(firstEvent.amount(), secondEvent.amount());
            assertEquals(firstEvent.currency(), secondEvent.currency());
            assertEquals(firstEvent.status(), secondEvent.status());
            assertEquals(firstEvent.latencyMs(), secondEvent.latencyMs());
        }
    }

    @Test
    void generatesDifferentIdsForSeparateEvents() {
        PaymentSimulator simulator = new PaymentSimulator(new Random(42));
        assertNotEquals(simulator.generateTransaction().id(), simulator.generateTransaction().id());
    }

    @Test
    void rejectsMissingRandomGenerator() {
        assertThrows(NullPointerException.class, () -> new PaymentSimulator(null));
    }
}
