package com.example.documents.domain.model;

import java.util.Objects;

/**
 * Represents a validation error with location and description.
 *
 * @param path the location in the document where the error occurred (e.g., "/Invoice/ID")
 * @param message a human-readable description of the error
 * @param code an error code for programmatic handling
 */
public record ValidationError(String path, String message, String code) {

    public ValidationError {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
        Objects.requireNonNull(code, "Code cannot be null");
    }

    /**
     * Creates a ValidationError with the given details.
     */
    public static ValidationError of(String path, String message, String code) {
        return new ValidationError(path, message, code);
    }

    @Override
    public String toString() {
        return "[" + code + "] " + path + ": " + message;
    }
}
