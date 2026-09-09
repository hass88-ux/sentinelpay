package com.sentinelpay.backend.simulator;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Currency;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import com.sentinelpay.backend.transaction.TransactionEvent;
import com.sentinelpay.backend.transaction.TransactionStatus;

/** Generates individual payment attempts using illustrative demo settings. */
public class PaymentSimulator {
    private static final Currency USD = Currency.getInstance("USD");
    private final Random random;
    private final SimulationSettings settings;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    public PaymentSimulator(Random random) {
        this(random, SimulationScenario.NORMAL.settings());
    }

    public PaymentSimulator(Random random, SimulationSettings settings) {
        this(random, settings, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    /** Use one simulator per run. The supplied random source and ID supplier may be stateful. */
    public PaymentSimulator(Random random, SimulationSettings settings, Clock clock, Supplier<String> idSupplier) {
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier must not be null");
    }

    public TransactionEvent generateTransaction() {
        // Widen before adding one so Integer.MAX_VALUE remains a valid inclusive bound.
        long amountCents = random.nextLong(settings.minAmountCents(), (long) settings.maxAmountCents() + 1);
        BigDecimal amount = BigDecimal.valueOf(amountCents, 2);
        TransactionStatus status = random.nextDouble() < settings.successProbability()
                ? TransactionStatus.SUCCESS : TransactionStatus.FAILED;
        long latencyMs = random.nextLong(settings.minLatencyMs(), (long) settings.maxLatencyMs() + 1);

        return new TransactionEvent(idSupplier.get(), clock.instant(),
                amount, USD, status, latencyMs);
    }
}
