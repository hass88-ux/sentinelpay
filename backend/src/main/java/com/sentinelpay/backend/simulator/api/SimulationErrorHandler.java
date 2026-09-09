package com.sentinelpay.backend.simulator.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = SimulationController.class)
public class SimulationErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidOption(IllegalArgumentException ex) {
        return problem(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail invalidNumber(MethodArgumentTypeMismatchException ex) {
        return problem(ex.getName() + " must be an integer in its supported range");
    }

    private ProblemDetail problem(String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid simulation request");
        return problem;
    }
}
