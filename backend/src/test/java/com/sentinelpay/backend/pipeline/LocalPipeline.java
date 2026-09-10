package com.sentinelpay.backend.pipeline;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Development-only launcher. Real services run locally; nothing is installed system-wide. */
public class LocalPipeline {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--verify")) {
            verify();
            return;
        }
        boolean incidentDemo = args.length == 1 && args[0].startsWith("--incident-demo=");
        int port = incidentDemo ? Integer.parseInt(args[0].substring("--incident-demo=".length()))
                : args.length == 0 ? 8081 : Integer.parseInt(args[0]);
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("port must be between 1024 and 65535");
        Path local = incidentDemo ? Files.createTempDirectory(Path.of("target"),"part4-demo-").toAbsolutePath()
                : Path.of(".local").toAbsolutePath();
        Files.createDirectories(local);
        try (var channel = FileChannel.open(local.resolve("pipeline.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("A local pipeline already owns this database directory.");
            try (var runtime = new TestPipeline(local.resolve("postgres"), port, true, true)) {
                var shutdown = new Thread(() -> {
                    try { runtime.close(); } catch (Exception ex) { System.err.println("Pipeline shutdown: " + ex.getMessage()); }
                }, "local-pipeline-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try {
                    if (incidentDemo) {
                        var result=IncidentDemo.seed(runtime);
                        System.out.println("Historical forecast: http://localhost:"+port
                                +"/api/intelligence/forecasts?currency=USD&asOf="+result.forecastAsOf());
                        System.out.println("Incident report: http://localhost:"+port+"/api/intelligence/incidents/"+result.caseId());
                    }
                    System.out.println("SentinelPay pipeline ready at http://localhost:" + port);
                    System.out.println("PostgreSQL data: " + local.resolve("postgres"));
                    System.out.println("Kafka logs: " + local.resolve("kafka"));
                    System.out.println("Press Enter to stop the local pipeline cleanly.");
                    new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
                } finally {
                    try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch (IllegalStateException ignored) { }
                }
            }
        }
    }

    private static HttpResponse<String> request(TestPipeline runtime, String method, String path) throws Exception {
        String port = runtime.context.getEnvironment().getProperty("local.server.port");
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).method(method, HttpRequest.BodyPublishers.noBody()).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void verify() throws Exception {
        Path data = Files.createTempDirectory(Path.of("target"), "part2-verify-").resolve("postgres");
        List<String> ids = new ArrayList<>();
        String pendingId;
        try (var runtime = new TestPipeline(data, 0)) {
            ObjectMapper mapper = runtime.context.getBean(ObjectMapper.class);
            var health = request(runtime, "GET", "/actuator/health");
            check(health.statusCode() == 200, "health endpoint");
            for (String scenario : new String[] {"NORMAL", "DEGRADED", "OUTAGE"}) {
                var response = request(runtime, "POST", "/api/pipeline/simulations?count=5&scenario=" + scenario + "&seed=42");
                check(response.statusCode() == 202, "publish " + scenario + ": " + response.body());
                var report = mapper.readTree(response.body());
                check(report.get("acknowledgedIds").size() == 5, "acknowledged batch size");
                for (JsonNode id : report.get("acknowledgedIds")) ids.add(id.asText());
            }
            var store = runtime.context.getBean(PaymentStore.class);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30))
                    .until(() -> ids.stream().allMatch(id -> store.find(id).isPresent()));
            for (String id : ids) check(request(runtime, "GET", "/api/pipeline/transactions/" + id).statusCode() == 200, "stored ID");
            var metrics = mapper.readTree(request(runtime, "GET", "/api/pipeline/metrics").body());
            long total = 0;
            for (JsonNode bucket : metrics) total += bucket.get("totalCount").asLong();
            check(total == 15, "minute metric total");
            check(request(runtime, "POST", "/api/pipeline/simulations?count=101").statusCode() == 400, "HTTP bounds");
            runtime.context.getBean(org.springframework.kafka.config.KafkaListenerEndpointRegistry.class).stop();
            var pending = new com.sentinelpay.backend.simulator.PaymentSimulator(new java.util.Random(9)).generateTransaction();
            pendingId = pending.id();
            runtime.context.getBean(PaymentPublisher.class).publish(List.of(pending));
            check(store.find(pendingId).isEmpty(), "pending event must not be consumed before restart");
        }
        // Both stored data and an acknowledged-but-unconsumed Kafka payment must survive.
        try (var restarted = new TestPipeline(data, 0)) {
            for (String id : ids) check(request(restarted, "GET", "/api/pipeline/transactions/" + id).statusCode() == 200, "persistence after restart");
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30))
                    .until(() -> restarted.context.getBean(PaymentStore.class).find(pendingId).isPresent());
        }
        System.out.println("Part 2 verification passed: all scenarios, 15 stored payments, metrics, validation, database persistence and pending Kafka delivery after restart.");
        System.out.println("Isolated verification database retained under " + data);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Verification failed: " + description);
    }
}
