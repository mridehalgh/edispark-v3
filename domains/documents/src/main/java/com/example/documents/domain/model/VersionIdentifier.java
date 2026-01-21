package com.example.documents.domain.model;

import java.util.Objects;

/**
 * A version identifier for schemas, typically following semantic versioning (e.g., "2.1.0").
 */
public record VersionIdentifier(String value) {

    public VersionIdentifier {
        Objects.requireNonNull(value, "Version identifier value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Version identifier cannot be blank");
        }
    }

    /**
     * Creates a VersionIdentifier from a string.
     */
    public static VersionIdentifier of(String value) {
        return new VersionIdentifier(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
