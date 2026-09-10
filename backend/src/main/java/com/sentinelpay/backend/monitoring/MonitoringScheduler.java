package com.sentinelpay.backend.monitoring;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("pipeline")
@ConditionalOnProperty(name="monitoring.scheduling-enabled", havingValue="true", matchIfMissing=true)
public class MonitoringScheduler {
    private final MonitoringService service;
    public MonitoringScheduler(MonitoringService service) { this.service = service; }
    @Scheduled(fixedDelay=30000, initialDelay=5000)
    public void scan() {
        try { service.run(); }
        catch (RuntimeException ex) {
            LoggerFactory.getLogger(MonitoringScheduler.class).error("Monitoring scan failed; will retry next cycle",ex);
        }
    }
}
