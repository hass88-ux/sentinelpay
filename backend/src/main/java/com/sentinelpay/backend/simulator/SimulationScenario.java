package com.sentinelpay.backend.simulator;

/** Synthetic conditions for exercising the application, not production measurements. */
public enum SimulationScenario {
    NORMAL(new SimulationSettings(1, 50_000, 10, 500, 0.90)),
    DEGRADED(new SimulationSettings(1, 50_000, 300, 2_000, 0.70)),
    OUTAGE(new SimulationSettings(1, 50_000, 1_000, 5_000, 0.10));

    private final SimulationSettings settings;

    SimulationScenario(SimulationSettings settings) {
        this.settings = settings;
    }

    public SimulationSettings settings() {
        return settings;
    }
}
