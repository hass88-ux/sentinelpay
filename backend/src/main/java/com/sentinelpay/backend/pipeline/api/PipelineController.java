package com.sentinelpay.backend.pipeline.api;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.sentinelpay.backend.pipeline.*;
import com.sentinelpay.backend.simulator.*;
import com.sentinelpay.backend.transaction.TransactionEvent;

@RestController
@Profile("pipeline")
@RequestMapping("/api/pipeline")
public class PipelineController {
    private final PaymentPublisher publisher;
    private final PaymentStore store;

    public PipelineController(PaymentPublisher publisher, PaymentStore store) {
        this.publisher = publisher;
        this.store = store;
    }

    @PostMapping("/simulations")
    public ResponseEntity<PublishReport> simulate(@RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "NORMAL") String scenario, @RequestParam(required = false) Long seed) {
        if (count < 1 || count > 100) throw new IllegalArgumentException("count must be between 1 and 100");
        SimulationScenario selected;
        try {
            selected = SimulationScenario.valueOf(scenario.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("scenario must be NORMAL, DEGRADED, or OUTAGE");
        }
        var simulator = new PaymentSimulator(seed == null ? new Random() : new Random(seed), selected.settings());
        var events = IntStream.range(0, count).mapToObj(i -> simulator.generateTransaction()).toList();
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(publisher.publish(events));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionEvent> find(@PathVariable String id) {
        return store.find(id).map(event -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(event))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionEvent>> recent(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.recent(limit));
    }

    @GetMapping("/metrics")
    public ResponseEntity<List<MinuteMetric>> metrics(@RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "60") int limit) {
        if (from == null && to == null) {
            to = Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(60);
            from = to.minusSeconds(3600);
        }
        if (from == null || to == null || !from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(1)) > 0
                || !from.equals(from.truncatedTo(ChronoUnit.MINUTES))
                || !to.equals(to.truncatedTo(ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("from and to must be minute-aligned UTC instants, ordered and at most 24 hours apart");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.metrics(from, to, limit));
    }
}
