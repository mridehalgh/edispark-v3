package com.edispark.integrationapplication.config;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class IntegrationExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_INTEGRATION_CONFIGURATION", exception.getMessage(), Instant.now()));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorBody> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("INTEGRATION_STATE_CONFLICT", exception.getMessage(), Instant.now()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST",
                "The request contains invalid or missing fields", Instant.now()));
    }
    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ErrorBody> dependency(RestClientResponseException exception) {
        HttpStatus status = exception.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new ErrorBody("INTEGRATION_DEPENDENCY_FAILED", "A dependent EdiSpark service rejected the request", Instant.now()));
    }
    record ErrorBody(String code, String message, Instant timestamp) { }
}
