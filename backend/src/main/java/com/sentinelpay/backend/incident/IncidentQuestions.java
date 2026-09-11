package com.sentinelpay.backend.incident;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** One question about one case; no conversation history or access to other cases. */
public final class IncidentQuestions {
    public record Observation(Instant bucket, long totalCount, long failedCount, BigDecimal averageLatencyMs) {}
    public record Answer(String question, String mode, String answer, List<String> evidenceRules,
            List<Observation> observations, OllamaExplainer.Explanation explanation) {}

    public static String validate(String question) {
        if (question == null || question.isBlank() || question.length() > 500)
            throw new IllegalArgumentException("question must contain 1 to 500 characters");
        return question.strip();
    }

    public Answer answer(IncidentStore.Snapshot snapshot, String question, OllamaExplainer explainer) {
        question = validate(question);
        var report = new Investigator().investigate(snapshot, Instant.now());
        var observations = snapshot.history().stream().map(m -> new Observation(m.bucket(),
                m.totalCount(), m.failedCount(), m.averageLatencyMs())).toList();
        var explanation = explainer.explain(report, question, observations);
        if (explanation.aiSuggestion() != null) {
            var rules = explanation.aiSuggestion().hypotheses().stream()
                    .flatMap(h -> h.evidenceRules().stream()).distinct().toList();
            return new Answer(question, explanation.mode(), explanation.aiSuggestion().summary(),
                    rules, observations, explanation);
        }
        String query = question.toLowerCase(Locale.ROOT);
        String answer;
        List<String> references = List.of();
        if (query.contains("cause") || query.contains("responsible")) {
            answer = "The root cause is unknown. These aggregate metrics cannot establish which dependency or processor caused the change. "
                    + String.join(" ", report.nextChecks());
        } else if (query.contains("before") || query.contains("changed") || query.contains("trend")) {
            var prior = observations.stream().filter(o -> o.bucket().isBefore(snapshot.incident().bucket())).toList();
            if (prior.size() < 2) answer = "There are not enough stored minutes before this case to describe a change.";
            else {
                var first = prior.getFirst();
                var last = prior.getLast();
                answer = "Before the case minute, the available observations run from " + first.bucket() + " to " + last.bucket()
                        + ". Average latency changed from " + first.averageLatencyMs() + " to " + last.averageLatencyMs()
                        + " ms; failures changed from " + first.failedCount() + "/" + first.totalCount()
                        + " to " + last.failedCount() + "/" + last.totalCount()
                        + " payments. See observations for gaps and intermediate values; this comparison does not establish a cause.";
                references = report.evidence().stream().map(IncidentStore.Evidence::rule)
                        .filter(r -> r.equals("AVERAGE_LATENCY") || r.equals("FAILURE_RATE")).toList();
            }
        } else if (query.contains("next") || query.contains("check") || query.contains("investigat")) {
            answer = String.join(" ", report.nextChecks());
            references = report.hypotheses().stream().flatMap(h -> h.evidenceRules().stream()).distinct().toList();
        } else if (query.contains("why") || query.contains("flag") || query.contains("alert")) {
            var alerts = report.evidence().stream().filter(e -> e.state().equals("WARNING") || e.state().equals("CRITICAL")).toList();
            answer = alerts.isEmpty() ? "No rule currently alerts for this historical case. Its state is " + report.incident().state() + "."
                    : alerts.stream().map(e -> e.rule() + " is " + e.state() + ": observed " + e.observed()
                        + ", warning threshold " + e.warningThreshold() + ", critical threshold " + e.criticalThreshold()
                        + ". " + e.explanation()).collect(java.util.stream.Collectors.joining(" "));
            references = alerts.stream().map(IncidentStore.Evidence::rule).toList();
        } else {
            answer = "The model is unavailable. I can explain why this case was flagged, compare observations before it, "
                    + "or list investigation checks. The evidence report is included; it may not answer your specific question.";
        }
        return new Answer(question, "DETERMINISTIC", answer, references, observations, explanation);
    }
}
