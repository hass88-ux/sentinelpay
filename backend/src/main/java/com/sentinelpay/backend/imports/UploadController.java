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
}
