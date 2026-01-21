package com.example.documents.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a DocumentVersion entity.
 */
public record DocumentVersionId(UUID value) {

    public DocumentVersionId {
        Objects.requireNonNull(value, "DocumentVersionId value cannot be null");
    }

    /**
     * Creates a new DocumentVersionId with a randomly generated UUID.
     */
    public static DocumentVersionId generate() {
        return new DocumentVersionId(UUID.randomUUID());
    }

    /**
     * Creates a DocumentVersionId from a string representation.
     */
    public static DocumentVersionId fromString(String value) {
        return new DocumentVersionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
