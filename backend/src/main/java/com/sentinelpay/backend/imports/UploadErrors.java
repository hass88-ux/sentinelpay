package com.sentinelpay.backend.imports;

import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes = UploadController.class)
@Profile("uploads")
public class UploadErrors {
    @ExceptionHandler(IllegalArgumentException.class) ProblemDetail invalid(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ProblemDetail large() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "CSV must be at most 2 MiB");
    }
    @ExceptionHandler({DataAccessException.class, IOException.class}) ProblemDetail unavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The upload could not be completed. Refresh your saved files before retrying.");
    }
}
