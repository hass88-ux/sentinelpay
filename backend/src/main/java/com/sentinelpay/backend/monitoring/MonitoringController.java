package com.sentinelpay.backend.monitoring;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("pipeline")
@RequestMapping("/api/monitoring")
public class MonitoringController {
    private final MonitoringService service;
    private final MonitoringStore store;
    public MonitoringController(MonitoringService service, MonitoringStore store) {
        this.service=service;
        this.store=store;
    }
    @PostMapping("/runs")
    public ResponseEntity<MonitoringStore.Run> run() {
        var result=service.run();
        return ResponseEntity.status(result.state().equals("BUSY") ? 409 : 200)
                .cacheControl(CacheControl.noStore()).body(result);
    }
    @GetMapping("/status")
    public ResponseEntity<MonitoringService.Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status());
    }
    @GetMapping("/findings")
    public ResponseEntity<List<MonitoringStore.Finding>> findings(
            @RequestParam(defaultValue="false") boolean includeResolved,
            @RequestParam(defaultValue="50") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.findings(includeResolved,limit));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,ex.getMessage());
    }
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail unavailable(DataAccessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Monitoring storage is temporarily unavailable.");
    }
}
