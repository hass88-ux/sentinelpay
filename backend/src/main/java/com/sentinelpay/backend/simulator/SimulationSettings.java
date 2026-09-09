package com.sentinelpay.backend.simulator;

/** USD amounts in cents; both amount and latency bounds are inclusive. */
public record SimulationSettings(int minAmountCents, int maxAmountCents,
        int minLatencyMs, int maxLatencyMs, double successProbability) {

    public SimulationSettings {
        if (minAmountCents <= 0 || maxAmountCents < minAmountCents) {
            throw new IllegalArgumentException("amount bounds must be positive and ordered");
        }
        if (minLatencyMs < 0 || maxLatencyMs < minLatencyMs) {
            throw new IllegalArgumentException("latency bounds must be nonnegative and ordered");
        }
        if (!Double.isFinite(successProbability) || successProbability < 0 || successProbability > 1) {
            throw new IllegalArgumentException("successProbability must be finite and between 0 and 1");
        }
    }
}
