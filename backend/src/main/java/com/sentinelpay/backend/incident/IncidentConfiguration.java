package com.sentinelpay.backend.incident;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.*;

@Configuration
@Profile("pipeline")
public class IncidentConfiguration {
    @Bean(destroyMethod="close")
    OllamaExplainer ollamaExplainer(org.springframework.core.env.Environment environment,
            tools.jackson.databind.ObjectMapper mapper) {
        return new OllamaExplainer(environment.getProperty("incident.ai.enabled",Boolean.class,false),
                environment.getProperty("incident.ai.model",""),
                java.net.URI.create("http://127.0.0.1:11434/api/generate"),java.time.Duration.ofSeconds(30),mapper);
    }
    @Bean
    IncidentStore incidentStore(DataSource source, Flyway pipelineMigrations) {
        return new IncidentStore(source);
    }
}
