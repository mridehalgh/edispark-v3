package com.edispark.mft.api.rest;

import com.edispark.mft.api.dto.MftErrorResponse;
import com.edispark.mft.application.MftNotFoundException;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice(basePackages = "com.edispark.mft")
public class MftExceptionHandler {
    @ExceptionHandler(MftNotFoundException.class)
    public ResponseEntity<MftErrorResponse> notFound(MftNotFoundException exception, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MftErrorResponse.actionable("MFT_NOT_FOUND", exception.getMessage(), "CONFIGURATION", false,
                        "Refresh the page and confirm that the partner, certificate or transfer still exists.",
                        correlationId(request)));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<MftErrorResponse> invalid(Exception exception, WebRequest request) {
        return ResponseEntity.badRequest().body(MftErrorResponse.actionable("INVALID_MFT_REQUEST",
                "The managed file transfer request is invalid", "REQUEST_VALIDATION", false,
                "Correct the invalid partner, certificate or payload field, then retry.", correlationId(request)));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<MftErrorResponse> storage(IOException exception, WebRequest request) {
        return storageProblem(exception, request.getHeader("X-Correlation-ID"));
    }

    ResponseEntity<MftErrorResponse> storageProblem(IOException exception, String requestedCorrelationId) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(MftErrorResponse.actionable("MFT_STORAGE_UNAVAILABLE", "Transfer storage is unavailable",
                        "PERSISTENCE", true,
                        "Try again shortly. If the problem continues, use the correlation ID when checking storage health.",
                        correlationId(requestedCorrelationId)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<MftErrorResponse> invalidState(IllegalStateException exception,
            WebRequest request) {
        String message = exception.getMessage() == null ? "The operation is not allowed in the current state" : exception.getMessage();
        String code = message.contains("assigned") || message.contains("referenced") || message.contains("unused draft")
                ? "MFT_CONFIGURATION_IN_USE" : "MFT_STATE_CONFLICT";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MftErrorResponse.actionable(code, message, "CONFIGURATION", false,
                        "Create a new revision or remove the live dependency identified by this error, then retry.",
                        correlationId(request)));
    }

    private String correlationId(WebRequest request) {
        return correlationId(request.getHeader("X-Correlation-ID"));
    }

    private String correlationId(String candidate) {
        return candidate == null || !candidate.matches("[A-Za-z0-9._:-]{1,128}")
                ? UUID.randomUUID().toString() : candidate;
    }
}
