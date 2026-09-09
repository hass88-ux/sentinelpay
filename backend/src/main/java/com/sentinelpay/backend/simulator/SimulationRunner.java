package com.sentinelpay.backend.simulator;

import java.util.Objects;
import java.util.function.Consumer;

import com.sentinelpay.backend.transaction.TransactionEvent;

/** Emits a finite sequence without retaining events in memory. */
public final class SimulationRunner {
    private SimulationRunner() {
    }

    static void validateLimits(int count, int intervalMs) {
        if (count < 1 || count > 10_000) {
            throw new IllegalArgumentException("count must be between 1 and 10000");
        }
        if (intervalMs < 0 || intervalMs > 1_000) {
            throw new IllegalArgumentException("interval-ms must be between 0 and 1000");
        }
    }

    public static void run(PaymentSimulator simulator, int count, int intervalMs,
            Consumer<TransactionEvent> output) throws InterruptedException {
        Objects.requireNonNull(simulator, "simulator must not be null");
        Objects.requireNonNull(output, "output must not be null");
        validateLimits(count, intervalMs);
        for (int i = 0; i < count; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("simulation interrupted");
            }
            output.accept(simulator.generateTransaction());
            if (i + 1 < count && intervalMs > 0) {
                Thread.sleep(intervalMs);
            }
        }
    }
}
