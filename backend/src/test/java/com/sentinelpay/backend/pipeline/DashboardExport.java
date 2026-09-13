package com.sentinelpay.backend.pipeline;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import com.sentinelpay.backend.incident.*;
import com.sentinelpay.backend.monitoring.MonitoringService;
import tools.jackson.databind.ObjectMapper;

/** Export only freshly generated synthetic data for the static dashboard demo. */
final class DashboardExport {
    static void export() throws Exception {
        try (var runtime = new TestPipeline()) {
            var fixture = IncidentDemo.seed(runtime);
            var store = runtime.context.getBean(PaymentStore.class);
            var cases = runtime.context.getBean(IncidentStore.class);
            var snapshot = cases.snapshot(fixture.caseId()).orElseThrow();
            var report = new Investigator().investigate(snapshot, Instant.now());
            var questions = new LinkedHashMap<String,Object>();
            for (var question : List.of("Why was this flagged?", "What changed before the spike?",
                    "What should I investigate next?", "What caused this incident?")) {
                questions.put(question, new IncidentQuestions().answer(snapshot, question,
                        runtime.context.getBean(OllamaExplainer.class)));
            }
            var result = new LinkedHashMap<String,Object>();
            result.put("capturedAt", Instant.now());
            result.put("source", "Java-generated synthetic fixture through Kafka and PostgreSQL");
            result.put("metrics", store.metrics(fixture.forecastAsOf().minusSeconds(240), fixture.forecastAsOf().plusSeconds(120), 100));
            result.put("cases", cases.recent(true, 100));
            result.put("reports", Map.of(fixture.caseId().toString(), report));
            result.put("transactions", store.recent(100));
            result.put("questions", Map.of(fixture.caseId().toString(), questions));
            result.put("forecasts", new TrendForecaster().forecast("USD",fixture.forecastAsOf(),cases.history("USD",fixture.forecastAsOf())));
            result.put("monitoring", runtime.context.getBean(MonitoringService.class).status());
            var file = Path.of("../frontend/public/demo/snapshot.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file,runtime.context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter().writeValueAsString(result));
            System.out.println("Exported synthetic dashboard data to " + file.toAbsolutePath().normalize());
        }
    }
}
