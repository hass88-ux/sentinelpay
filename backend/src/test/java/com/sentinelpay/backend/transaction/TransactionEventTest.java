package com.sentinelpay.backend.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class TransactionEventTest {
    private static final Instant TIME = Instant.parse("2026-09-08T12:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("19.99");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void preservesCompletedPaymentDetails() {
        TransactionEvent event = new TransactionEvent("txn-1", TIME, AMOUNT, USD,
                TransactionStatus.SUCCESS, 125);
        assertEquals("txn-1", event.id());
        assertEquals(TIME, event.timestamp());
        assertEquals(AMOUNT, event.amount());
        assertEquals(USD, event.currency());
        assertEquals(TransactionStatus.SUCCESS, event.status());
        assertEquals(125L, event.latencyMs());
    }

    @Test
    void acceptsFailedPaymentAndZeroLatency() {
        TransactionEvent event = new TransactionEvent("txn-2", TIME, AMOUNT, USD,
                TransactionStatus.FAILED, 0);
        assertEquals(TransactionStatus.FAILED, event.status());
        assertEquals(0L, event.latencyMs());
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThrows(NullPointerException.class, () -> new TransactionEvent(
                null, TIME, AMOUNT, USD, TransactionStatus.SUCCESS, 125));
        assertThrows(NullPointerException.class, () -> new TransactionEvent(
                "txn-1", null, AMOUNT, USD, TransactionStatus.SUCCESS, 125));
        assertThrows(NullPointerException.class, () -> new TransactionEvent(
                "txn-1", TIME, null, USD, TransactionStatus.SUCCESS, 125));
        assertThrows(NullPointerException.class, () -> new TransactionEvent(
                "txn-1", TIME, AMOUNT, null, TransactionStatus.SUCCESS, 125));
        assertThrows(NullPointerException.class, () -> new TransactionEvent(
                "txn-1", TIME, AMOUNT, USD, null, 125));
    }

    @Test
    void rejectsBlankIds() {
        for (String id : new String[] {"", "   ", "\t\n"}) {
            assertThrows(IllegalArgumentException.class, () -> new TransactionEvent(
                    id, TIME, AMOUNT, USD, TransactionStatus.SUCCESS, 125));
        }
    }

    @Test
    void rejectsNonpositiveAmounts() {
        for (BigDecimal amount : new BigDecimal[] {BigDecimal.ZERO, new BigDecimal("-0.01")}) {
            assertThrows(IllegalArgumentException.class, () -> new TransactionEvent(
                    "txn-1", TIME, amount, USD, TransactionStatus.SUCCESS, 125));
        }
    }

    @Test
    void rejectsNegativeLatency() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionEvent(
                "txn-1", TIME, AMOUNT, USD, TransactionStatus.SUCCESS, -1));
    }
}
