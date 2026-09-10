package com.sentinelpay.backend.pipeline.api;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.sentinelpay.backend.pipeline.*;

@Profile("pipeline")
@RestControllerAdvice(assignableTypes = PipelineController.class)
public class PipelineErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidType(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "invalid value for " + ex.getName());
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail storageUnavailable(DataAccessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Payment storage is temporarily unavailable.");
    }

    @ExceptionHandler(PublishUnavailableException.class)
    ResponseEntity<PublishReport> publishingUnavailable(PublishUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).body(ex.report());
    }
}
