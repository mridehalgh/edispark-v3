package com.edispark.integrationapplication.config;

import java.time.Instant;
import java.util.Map;
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
        return ResponseEntity.badRequest().body(ErrorBody.of("INVALID_INTEGRATION_CONFIGURATION", exception.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorBody> conflict(IllegalStateException exception) {
        String message = exception.getMessage() == null ? "The operation is not allowed in the current state" : exception.getMessage();
        String code = message.contains("used") || message.contains("referenced") || message.contains("unused draft")
                ? "CONFIGURATION_IN_USE" : "INTEGRATION_STATE_CONFLICT";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(code, message, Instant.now(),
                Map.of("resolution", "Create a new revision or remove the listed live dependency before retrying")));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(ErrorBody.of("INVALID_REQUEST",
                "The request contains invalid or missing fields"));
    }
    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ErrorBody> dependency(RestClientResponseException exception) {
        HttpStatus status = exception.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(ErrorBody.of("INTEGRATION_DEPENDENCY_FAILED", "A dependent EdiSpark service rejected the request"));
    }
    record ErrorBody(String code, String message, Instant timestamp, Map<String, Object> details) {
        static ErrorBody of(String code, String message) { return new ErrorBody(code, message, Instant.now(), Map.of()); }
    }
}
