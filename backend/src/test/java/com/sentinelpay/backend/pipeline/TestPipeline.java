package com.sentinelpay.backend.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import com.sentinelpay.backend.BackendApplication;

/** Owns isolated real infrastructure; close the app before stopping its dependencies. */
final class TestPipeline implements AutoCloseable {
    final EmbeddedPostgres postgres;
    final EmbeddedKafkaKraftBroker kafka;
    ConfigurableApplicationContext context;

    TestPipeline() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        kafka = new EmbeddedKafkaKraftBroker(1, 3);
        try {
            kafka.afterPropertiesSet();
            var application = new SpringApplication(BackendApplication.class);
            application.setAdditionalProfiles("pipeline");
            context = application.run("--server.port=0", "--server.address=127.0.0.1",
                    "--pipeline.bootstrap-servers=" + kafka.getBrokersAsString(),
                    "--pipeline.jdbc-url=" + postgres.getJdbcUrl("postgres", "postgres"),
                    "--pipeline.username=postgres", "--pipeline.password=postgres");
        } catch (Exception ex) {
            close();
            throw ex;
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (context != null) context.close();
        } finally {
            try { kafka.destroy(); } finally { postgres.close(); }
        }
    }
}
