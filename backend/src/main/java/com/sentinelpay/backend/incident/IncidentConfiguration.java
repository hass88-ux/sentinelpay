package com.sentinelpay.backend.incident;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.*;

@Configuration
@Profile("pipeline")
public class IncidentConfiguration {
    @Bean
    IncidentStore incidentStore(DataSource source, Flyway pipelineMigrations) {
        return new IncidentStore(source);
    }
}
