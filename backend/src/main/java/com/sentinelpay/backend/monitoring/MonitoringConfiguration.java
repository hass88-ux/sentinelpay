package com.sentinelpay.backend.monitoring;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("pipeline")
@EnableScheduling
public class MonitoringConfiguration {
    @Bean
    MonitoringStore monitoringStore(DataSource source, Flyway pipelineMigrations) {
        return new MonitoringStore(source);
    }
}
