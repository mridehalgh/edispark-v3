package com.example.documents.domain.model;

import java.util.Objects;

/**
 * Represents a validation warning with location and description.
 *
 * @param path the location in the document where the warning applies
 * @param message a human-readable description of the warning
 */
public record ValidationWarning(String path, String message) {

    public ValidationWarning {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
    }

    /**
     * Creates a ValidationWarning with the given details.
     */
    public static ValidationWarning of(String path, String message) {
        return new ValidationWarning(path, message);
    }

    @Override
    public String toString() {
        return path + ": " + message;
    }
}
