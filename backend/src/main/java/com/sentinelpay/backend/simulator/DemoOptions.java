package com.sentinelpay.backend.simulator;

import java.util.HashSet;
import java.util.Locale;

/** Small dependency-free parser for the finite console demo. */
record DemoOptions(int count, int intervalMs, SimulationScenario scenario, Long seed) {
    static final String USAGE = "Usage: PaymentSimulatorDemo [--count=1..10000] [--interval-ms=0..1000] "
            + "[--scenario=NORMAL|DEGRADED|OUTAGE] [--seed=<long>] | --help";

    static DemoOptions parse(String[] args) {
        int count = 10;
        int intervalMs = 0;
        SimulationScenario scenario = SimulationScenario.NORMAL;
        Long seed = null;
        var seen = new HashSet<String>();
        for (String arg : args) {
            String[] option = arg.split("=", 2);
            if (option.length != 2 || option[1].isBlank()) {
                throw new IllegalArgumentException("options must use --name=value");
            }
            if (!seen.add(option[0])) {
                throw new IllegalArgumentException("duplicate option: " + option[0]);
            }
            try {
                switch (option[0]) {
                    case "--count" -> count = Integer.parseInt(option[1]);
                    case "--interval-ms" -> intervalMs = Integer.parseInt(option[1]);
                    case "--seed" -> seed = Long.parseLong(option[1]);
                    case "--scenario" -> scenario = SimulationScenario.valueOf(option[1].toUpperCase(Locale.ROOT));
                    default -> throw new IllegalArgumentException("unknown option: " + option[0]);
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(option[0] + " requires an integer in its supported range");
            }
        }
        SimulationRunner.validateLimits(count, intervalMs);
        return new DemoOptions(count, intervalMs, scenario, seed);
    }
}
