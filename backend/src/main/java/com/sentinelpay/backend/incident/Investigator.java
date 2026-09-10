package com.sentinelpay.backend.incident;

import java.time.Instant;
import java.util.*;

/** Separates measured evidence from hypotheses that require further investigation. */
public final class Investigator {
    public record Hypothesis(String suggestion, List<String> evidenceRules) {}
    public record Report(Instant generatedAt, IncidentStore.Incident incident, String summary,
            List<IncidentStore.Evidence> evidence, List<TrendForecaster.Forecast> forecasts,
            List<Hypothesis> hypotheses, List<String> nextChecks, List<String> limitations) {}

    public Report investigate(IncidentStore.Snapshot snapshot, Instant now) {
        var incident = snapshot.incident();
        var hypotheses = new ArrayList<Hypothesis>();
        var checks = new ArrayList<String>();
        for (var item : snapshot.evidence()) {
            if (!Set.of("WARNING","CRITICAL").contains(item.state())) continue;
            switch (item.rule()) {
                case "FAILURE_RATE" -> {
                    hypotheses.add(new Hypothesis("Payment rejection or processing errors may be contributing; error codes are needed to distinguish them.",List.of(item.rule())));
                    checks.add("Inspect failure codes and compare affected processors for this minute.");
                }
                case "AVERAGE_LATENCY" -> {
                    hypotheses.add(new Hypothesis("Processing delays may be contributing; these aggregate metrics cannot identify the slow dependency.",List.of(item.rule())));
                    checks.add("Compare dependency timings and queue depth with the affected minute.");
                }
                case "VOLUME_DROP" -> {
                    hypotheses.add(new Hypothesis("Reduced demand or interrupted event delivery could explain the volume change; neither is established by this signal.",List.of(item.rule())));
                    checks.add("Compare upstream traffic counts with Kafka consumer lag before attributing the drop to demand.");
                }
                default -> { }
            }
        }
        if (checks.isEmpty()) checks.add("Review the correction history and confirm no recent bucket is still elevated.");
        var forecasts = new TrendForecaster().forecast(incident.currency(),incident.bucket(),snapshot.history());
        return new Report(now,incident,
                incident.currency()+" payment case for "+incident.bucket()+" is "+incident.state()
                    +" with "+incident.severity()+" severity; "+hypotheses.size()+" rules currently alert.",
                List.copyOf(snapshot.evidence()),forecasts,List.copyOf(hypotheses),List.copyOf(checks),List.of(
                    "This case describes a historical minute, not necessarily a currently active outage.",
                    "Root cause is unknown. Suggested causes require independent evidence.",
                    "Rule evidence is from the recorded evaluation time. Forecasts use currently stored metrics through the case minute; rerun monitoring after late arrivals.",
                    "Forecasts are an uncalibrated linear baseline, not incident probabilities."));
    }
}
