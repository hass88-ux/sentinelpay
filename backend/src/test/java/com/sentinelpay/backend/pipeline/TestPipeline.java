package com.sentinelpay.backend.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import com.sentinelpay.backend.BackendApplication;

/** Owns isolated real infrastructure; close the app before stopping its dependencies. */
final class TestPipeline implements AutoCloseable {
    final EmbeddedPostgres postgres;
    LocalKafka kafka;
    ConfigurableApplicationContext context;
    private boolean closed;

    TestPipeline() throws Exception {
        this(null, 0);
    }

    TestPipeline(java.nio.file.Path dataDirectory, int port) throws Exception {
        this(dataDirectory, port, false);
    }

    TestPipeline(java.nio.file.Path dataDirectory, int port, boolean monitoringEnabled) throws Exception {
        this(dataDirectory,port,monitoringEnabled,false);
    }

    TestPipeline(java.nio.file.Path dataDirectory, int port, boolean monitoringEnabled, boolean allowAi) throws Exception {
        var builder = EmbeddedPostgres.builder().setServerConfig("listen_addresses", "127.0.0.1");
        if (dataDirectory != null) builder.setDataDirectory(dataDirectory).setCleanDataDirectory(false);
        postgres = builder.start();
        try {
            var kafkaDirectory = dataDirectory == null
                    ? java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"), "part2-kafka-")
                    : dataDirectory.resolveSibling("kafka");
            kafka = new LocalKafka(kafkaDirectory);
            var application = new SpringApplication(BackendApplication.class);
            application.setAdditionalProfiles("pipeline");
            application.setRegisterShutdownHook(false);
            context = application.run("--server.port=" + port, "--server.address=127.0.0.1",
                    "--monitoring.scheduling-enabled=" + monitoringEnabled,
                    "--incident.ai.enabled=" + (allowAi && Boolean.parseBoolean(System.getenv("SENTINELPAY_AI_ENABLED"))),
                    "--pipeline.bootstrap-servers=" + kafka.getBrokersAsString(),
                    "--pipeline.jdbc-url=" + postgres.getJdbcUrl("postgres", "postgres"),
                    "--pipeline.username=postgres", "--pipeline.password=postgres");
        } catch (Exception ex) {
            close();
            throw ex;
        }
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) return;
        closed = true;
        try {
            if (context != null) context.close();
        } finally {
            try { if (kafka != null) kafka.close(); } finally { postgres.close(); }
        }
    }
}
