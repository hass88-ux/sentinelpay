package com.sentinelpay.backend.imports;

import com.sentinelpay.backend.monitoring.PaymentDetector;
import com.sentinelpay.backend.pipeline.MinuteMetric;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("uploads")
@RequestMapping("/api/uploads")
public class UploadController {
    private final PrivateUploadStore store;
    private final TransactionCsvReader csv = new TransactionCsvReader();
    private final PaymentDetector detector = new PaymentDetector();
    public UploadController(PrivateUploadStore store) { this.store = store; }
    public record Analysis(PrivateUploadStore.Upload upload, List<MinuteMetric> metrics, List<Finding> findings) {}
    public record Finding(java.time.Instant bucket, String currency, List<PaymentDetector.Evaluation> evaluations) {}

    @GetMapping public List<PrivateUploadStore.Upload> list(@AuthenticationPrincipal Jwt jwt) { return store.list(owner(jwt)); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PrivateUploadStore.Upload upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) throws IOException {
        limit(owner(jwt), "upload", 5, 50);
        if (file.getSize() > TransactionCsvReader.MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV must be at most 2 MiB");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv") || name.contains("/") || name.contains("\\"))
            throw new IllegalArgumentException("Choose a CSV file with a plain filename");
        try (var input = file.getInputStream()) { return store.save(owner(jwt), name, csv.read(input)); }
    }
    @GetMapping("/{id}") public Analysis read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID owner = owner(jwt);
        var upload = store.find(owner, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var metrics = store.metrics(owner, id);
        var findings = metrics.stream().map(m -> {
            var evaluations = new ArrayList<>(detector.evaluate(m));
            evaluations.add(detector.volume(m, metrics));
            return new Finding(m.bucket(), m.currency(), List.copyOf(evaluations));
        }).toList();
        return new Analysis(upload, metrics, findings);
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        if (!store.delete(owner(jwt), id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    public record AiContextRequest(String currency) {}
    public record Observation(java.time.Instant bucket, long totalCount, long failedCount, java.math.BigDecimal averageLatencyMs) {}
    public record AiContext(String source, String currency, List<Observation> observations, List<Finding> findings) {}

    @PostMapping("/{id}/ai-context")
    public AiContext aiContext(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody AiContextRequest request) {
        if (request.currency() == null || !request.currency().matches("[A-Z]{3}"))
            throw new IllegalArgumentException("Choose a currency in this file");
        // Authenticate ownership before reserving AI quota or returning any evidence.
        var analysis = read(jwt, id);
        var metrics = analysis.metrics().stream().filter(m->m.currency().equals(request.currency())).toList();
        if(metrics.isEmpty()) throw new IllegalArgumentException("Choose a currency in this file");
        if(metrics.size()>120) throw new IllegalArgumentException("AI supports up to 120 observed minutes per currency. Upload a smaller time window.");
        limit(owner(jwt), "ai", 3, 20);
        limit(new UUID(0,0), "ai-global", 12, 200);
        return new AiContext("Private uploaded payment metrics", request.currency(),
            metrics.stream().map(m->new Observation(m.bucket(),m.totalCount(),m.failedCount(),m.averageLatencyMs())).toList(),
            analysis.findings().stream().filter(f->f.currency().equals(request.currency())).toList());
    }

    private void limit(UUID owner, String operation, int minute, int day) {
        if(!store.reserveRequest(owner,operation,minute,day)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
            "Request limit reached. Please try later. Uploads allow 5/minute and 50/day; AI allows 3/minute and 20/day per account, subject to shared capacity.");
    }
}
