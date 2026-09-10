package com.sentinelpay.backend.pipeline;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.metadata.storage.Formatter;
import org.apache.kafka.server.common.MetadataVersion;
import kafka.server.KafkaConfig;
import kafka.server.KafkaRaftServer;

/** Development-only single-node KRaft broker with explicit loopback listeners and durable logs. */
final class LocalKafka implements AutoCloseable {
    private final KafkaRaftServer server;
    private final int port;

    LocalKafka(Path directory) throws Exception {
        Files.createDirectories(directory);
        directory = directory.toAbsolutePath();
        int controllerPort;
        try (var brokerSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                var controllerSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = brokerSocket.getLocalPort();
            controllerPort = controllerSocket.getLocalPort();
        }
        if (!Files.exists(directory.resolve("meta.properties"))) {
            new Formatter().setNodeId(0).setClusterId(Uuid.randomUuid().toString())
                    .setControllerListenerName("CONTROLLER").setHasDynamicQuorum(false)
                    .setReleaseVersion(MetadataVersion.latestProduction())
                    .addDirectory(directory.toString()).run();
        }
        var config = new Properties();
        config.setProperty("node.id", "0");
        config.setProperty("process.roles", "broker,controller");
        config.setProperty("listeners", "PLAINTEXT://127.0.0.1:" + port + ",CONTROLLER://127.0.0.1:" + controllerPort);
        config.setProperty("advertised.listeners", "PLAINTEXT://127.0.0.1:" + port);
        config.setProperty("listener.security.protocol.map", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT");
        config.setProperty("inter.broker.listener.name", "PLAINTEXT");
        config.setProperty("controller.listener.names", "CONTROLLER");
        config.setProperty("controller.quorum.voters", "0@127.0.0.1:" + controllerPort);
        config.setProperty("log.dirs", directory.toString());
        config.setProperty("num.partitions", "3");
        config.setProperty("offsets.topic.replication.factor", "1");
        config.setProperty("transaction.state.log.replication.factor", "1");
        config.setProperty("transaction.state.log.min.isr", "1");
        config.setProperty("group.initial.rebalance.delay.ms", "0");
        server = new KafkaRaftServer(KafkaConfig.fromProps(config), Time.SYSTEM);
        try {
            server.startup();
        } catch (Exception ex) {
            close();
            throw ex;
        }
    }

    String getBrokersAsString() {
        return "127.0.0.1:" + port;
    }

    @Override
    public void close() {
        server.shutdown();
        server.awaitShutdown();
    }
}
