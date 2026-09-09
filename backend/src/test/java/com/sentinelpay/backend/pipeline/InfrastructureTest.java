package com.sentinelpay.backend.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/** Uses real native PostgreSQL and a real single-node Kafka broker. */
class InfrastructureTest {
    @Test
    void startsLocalKafkaAndPostgresWithoutDocker() throws Exception {
        try (var postgres = EmbeddedPostgres.builder().start()) {
            try (var connection = postgres.getPostgresDatabase().getConnection();
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("select 1")) {
                rows.next();
                assertEquals(1, rows.getInt(1));
            }
            var kafka = new EmbeddedKafkaKraftBroker(1, 1, "infrastructure-check");
            try {
                kafka.afterPropertiesSet();
                assertEquals(1, kafka.getTopics().size());
            } finally {
                kafka.destroy();
            }
        }
    }
}
