package com.sentinelpay.backend.incident;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("pipeline")
@RequestMapping("/api/intelligence")
public class IncidentController {
    private final IncidentStore store;
    public IncidentController(IncidentStore store) { this.store=store; }

    @GetMapping("/incidents")
    public ResponseEntity<List<IncidentStore.Incident>> cases(
            @RequestParam(defaultValue="false") boolean includeCleared,
            @RequestParam(defaultValue="50") int limit) {
        return response(store.recent(includeCleared,limit));
    }
    @GetMapping("/incidents/{id}")
    public ResponseEntity<Investigator.Report> report(@PathVariable UUID id) {
        return response(investigate(id));
    }
    @GetMapping("/forecasts")
    public ResponseEntity<List<TrendForecaster.Forecast>> forecasts(@RequestParam(defaultValue="USD") String currency) {
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        Currency.getInstance(currency);
        var asOf=Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MINUTES).minusSeconds(60);
        return response(new TrendForecaster().forecast(currency,asOf,store.history(currency,asOf)));
    }
    Investigator.Report investigate(UUID id) {
        var snapshot=store.snapshot(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident case not found"));
        return new Investigator().investigate(snapshot,Instant.now());
    }
    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,ex.getMessage());
    }
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail unavailable(DataAccessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Incident storage is temporarily unavailable.");
    }
}
