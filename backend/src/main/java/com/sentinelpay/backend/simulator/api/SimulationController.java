package com.sentinelpay.backend.simulator.api;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinelpay.backend.simulator.PaymentSimulator;
import com.sentinelpay.backend.simulator.SimulationScenario;
import com.sentinelpay.backend.transaction.TransactionEvent;

@RestController
public class SimulationController {
    @GetMapping("/api/simulator/transactions")
    public ResponseEntity<SimulationPreview> preview(
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "NORMAL") String scenario,
            @RequestParam(required = false) Long seed) {
        if (count < 1 || count > 100) {
            throw new IllegalArgumentException("count must be between 1 and 100");
        }
        SimulationScenario selected;
        try {
            selected = SimulationScenario.valueOf(scenario.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("scenario must be NORMAL, DEGRADED, or OUTAGE");
        }

        // Request-local state keeps seeds independent across clients and repeated calls.
        var simulator = new PaymentSimulator(seed == null ? new Random() : new Random(seed), selected.settings());
        var events = new ArrayList<TransactionEvent>(count);
        for (int i = 0; i < count; i++) {
            events.add(simulator.generateTransaction());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SimulationPreview(selected, events.size(), events));
    }
}
