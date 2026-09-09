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
    private final Random random;
    private final SimulationSettings settings;

    public PaymentSimulator(Random random) {
        this(random, SimulationScenario.NORMAL.settings());
    }

    public PaymentSimulator(Random random, SimulationSettings settings) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    public TransactionEvent generateTransaction() {
        // Widen before adding one so Integer.MAX_VALUE remains a valid inclusive bound.
        long amountCents = random.nextLong(settings.minAmountCents(), (long) settings.maxAmountCents() + 1);
        BigDecimal amount = BigDecimal.valueOf(amountCents, 2);
        TransactionStatus status = random.nextDouble() < settings.successProbability()
                ? TransactionStatus.SUCCESS : TransactionStatus.FAILED;
        long latencyMs = random.nextLong(settings.minLatencyMs(), (long) settings.maxLatencyMs() + 1);

        return new TransactionEvent(UUID.randomUUID().toString(), Instant.now(),
                amount, USD, status, latencyMs);
    }
}
