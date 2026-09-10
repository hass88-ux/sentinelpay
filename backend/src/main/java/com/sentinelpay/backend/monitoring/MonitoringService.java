package com.sentinelpay.backend.monitoring;

import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("pipeline")
public class MonitoringService {
    public record Status(String state, MonitoringStore.Run lastSuccessfulRun, Instant lastFailureAt) {}
    private final MonitoringStore store;
    private volatile Status status = new Status("NOT_RUN", null, null);
    public MonitoringService(MonitoringStore store) { this.store = store; }

    public synchronized MonitoringStore.Run run() {
        try {
            var result = store.evaluate(Instant.now());
            if (!result.state().equals("BUSY")) status = new Status(result.state(),result,null);
            return result;
        } catch (RuntimeException ex) {
            status = new Status("FAILED",status.lastSuccessfulRun(),Instant.now());
            throw ex;
        }
    }
    public Status status() { return status; }
}
