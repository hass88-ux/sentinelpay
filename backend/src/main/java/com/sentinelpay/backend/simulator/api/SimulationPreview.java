package com.sentinelpay.backend.simulator.api;

import java.util.List;

import com.sentinelpay.backend.simulator.SimulationScenario;
import com.sentinelpay.backend.transaction.TransactionEvent;

/** A bounded preview response; events are not retained by the server. */
public record SimulationPreview(SimulationScenario scenario, int count, List<TransactionEvent> events) {
    public SimulationPreview {
        events = List.copyOf(events);
    }
}
