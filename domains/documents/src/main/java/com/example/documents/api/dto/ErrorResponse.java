package com.example.documents.api.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Response DTO representing an error response from the API.
 *
 * <p>Provides a consistent error structure across all API endpoints with an error code,
 * human-readable message, timestamp, and optional details map for additional context.
 *
 * <p>Use the factory methods {@link #of(String, String)} and {@link #of(String, String, Map)}
 * for convenient construction with automatic timestamp generation.
 *
 * @param code the error code identifying the type of error (e.g., "DOCUMENT_NOT_FOUND")
 * @param message a human-readable description of the error
 * @param timestamp the instant when the error occurred
 * @param details optional additional details about the error (e.g., field-level validation errors)
 */
public record ErrorResponse(
    String code,
    String message,
    Instant timestamp,
    Map<String, Object> details
) {
    /**
     * Creates an error response with the current timestamp and no additional details.
     *
     * @param code the error code identifying the type of error
     * @param message a human-readable description of the error
     * @return a new ErrorResponse with the current timestamp and empty details
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now(), Map.of());
    }

    /**
     * Creates an error response with the current timestamp and additional details.
     *
     * @param code the error code identifying the type of error
     * @param message a human-readable description of the error
     * @param details additional context about the error
     * @return a new ErrorResponse with the current timestamp and the provided details
     */
    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(code, message, Instant.now(), details);
    }
}
