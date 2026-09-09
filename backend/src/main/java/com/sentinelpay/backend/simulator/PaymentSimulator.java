package com.sentinelpay.backend.simulator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import com.sentinelpay.backend.transaction.TransactionEvent;
import com.sentinelpay.backend.transaction.TransactionStatus;

/** Generates individual payment attempts using illustrative demo settings. */
public class PaymentSimulator {
    private static final Currency USD = Currency.getInstance("USD");
    private static final int MIN_AMOUNT_CENTS = 1;
    private static final int MAX_AMOUNT_CENTS = 50_000;
    private static final int MIN_LATENCY_MS = 10;
    private static final int MAX_LATENCY_MS = 500;
    private static final double SUCCESS_PROBABILITY = 0.90;

    private final Random random;

    public PaymentSimulator(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    public TransactionEvent generateTransaction() {
        int amountCents = random.nextInt(MIN_AMOUNT_CENTS, MAX_AMOUNT_CENTS + 1);
        BigDecimal amount = BigDecimal.valueOf(amountCents, 2);
        TransactionStatus status = random.nextDouble() < SUCCESS_PROBABILITY
                ? TransactionStatus.SUCCESS : TransactionStatus.FAILED;
        int latencyMs = random.nextInt(MIN_LATENCY_MS, MAX_LATENCY_MS + 1);

        return new TransactionEvent(UUID.randomUUID().toString(), Instant.now(),
                amount, USD, status, latencyMs);
    }
}
