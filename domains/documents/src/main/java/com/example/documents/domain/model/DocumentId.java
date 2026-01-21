package com.example.documents.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a Document entity within a DocumentSet.
 */
public record DocumentId(UUID value) {

    public DocumentId {
        Objects.requireNonNull(value, "DocumentId value cannot be null");
    }

    /**
     * Creates a new DocumentId with a randomly generated UUID.
     */
    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID());
    }

    /**
     * Creates a DocumentId from a string representation.
     */
    public static DocumentId fromString(String value) {
        return new DocumentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
