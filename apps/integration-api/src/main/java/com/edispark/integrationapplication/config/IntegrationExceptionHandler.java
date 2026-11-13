package com.edispark.integrationapplication.config;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(basePackages = {"com.edispark.transformations.api", "com.edispark.partnerships.api",
        "com.edispark.exchange.api", "com.edispark.integrationapplication"})
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class IntegrationExceptionHandler {
    @ExceptionHandler(com.edispark.transformations.application.RetainedSourceChanged.class)
    ResponseEntity<ActionableErrorBody> staleSource(com.edispark.transformations.application.RetainedSourceChanged exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ActionableErrorBody.of("DOCUMENT_SOURCE_CHANGED",
                exception.getMessage(), "SOURCE_VALIDATION", false,
                "Return to the document and run readiness again to load its current source.", correlationId(request)));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ActionableErrorBody> invalid(IllegalArgumentException exception, HttpServletRequest request) {
        String message = safeMessage(exception, "The integration configuration is invalid");
        if (message.contains("Published mapping was not found") || message.contains("capability schemas")) {
            return ResponseEntity.badRequest().body(ActionableErrorBody.of("COMPATIBLE_MAPPING_NOT_FOUND", message,
                    "CONFIGURATION", false,
                    "Select or publish a mapping with the same direction, document type, standard and release, then retry.",
                    correlationId(request)));
        }
        return ResponseEntity.badRequest().body(ActionableErrorBody.of("INVALID_INTEGRATION_CONFIGURATION", message,
                "CONFIGURATION", false, "Correct the identified configuration value, then retry.",
                correlationId(request)));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ActionableErrorBody> conflict(IllegalStateException exception, HttpServletRequest request) {
        String message = safeMessage(exception, "The operation is not allowed in the current state");
        String code = message.contains("used") || message.contains("referenced") || message.contains("unused draft")
                ? "CONFIGURATION_IN_USE" : "INTEGRATION_STATE_CONFLICT";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ActionableErrorBody.of(code, message,
                "CONFIGURATION", false,
                "Create a new revision or remove the listed live dependency before retrying.",
                correlationId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ActionableErrorBody> validation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, Object> details = Map.of("fields", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "issue",
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList());
        return ResponseEntity.badRequest().body(new ActionableErrorBody("INVALID_REQUEST",
                "The request contains invalid or missing fields", Instant.now(), "REQUEST_VALIDATION", false,
                "Correct the listed fields, then retry.", correlationId(request), details));
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ActionableErrorBody> dependency(RestClientResponseException exception,
            HttpServletRequest request) {
        if (exception.getStatusCode().value() == 404) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ActionableErrorBody.of(
                    "INTEGRATION_DEPENDENCY_NOT_FOUND", "A required EdiSpark resource was not found",
                    "DEPENDENCY", false, "Refresh the page and confirm that the referenced resource still exists.",
                    correlationId(request)));
        }
        boolean retryable = exception.getStatusCode().value() == 429 || exception.getStatusCode().is5xxServerError();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ActionableErrorBody.of(
                retryable ? "INTEGRATION_DEPENDENCY_UNAVAILABLE" : "INTEGRATION_DEPENDENCY_REJECTED",
                retryable ? "A dependent EdiSpark service is temporarily unavailable"
                        : "A dependent EdiSpark service rejected the request",
                "DEPENDENCY", retryable,
                retryable ? "Try again shortly. If the problem continues, use the correlation ID when checking service health."
                        : "Correct the referenced resource or configuration before retrying.",
                correlationId(request)));
    }

    private String safeMessage(RuntimeException exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? fallback : exception.getMessage();
    }

    private String correlationId(HttpServletRequest request) {
        String candidate = request.getHeader("X-Correlation-ID");
        return candidate == null || !candidate.matches("[A-Za-z0-9._:-]{1,128}")
                ? UUID.randomUUID().toString() : candidate;
    }

    public record ActionableErrorBody(String code, String message, Instant timestamp, String stage,
            boolean retryable, String recommendedAction, String correlationId, Map<String, Object> details) {
        static ActionableErrorBody of(String code, String message, String stage, boolean retryable,
                String recommendedAction, String correlationId) {
            return new ActionableErrorBody(code, message, Instant.now(), stage, retryable, recommendedAction,
                    correlationId, Map.of());
        }
    }
}
