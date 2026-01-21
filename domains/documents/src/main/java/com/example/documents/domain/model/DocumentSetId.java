package com.example.documents.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a DocumentSet aggregate.
 */
public record DocumentSetId(UUID value) {

    public DocumentSetId {
        Objects.requireNonNull(value, "DocumentSetId value cannot be null");
    }

    /**
     * Creates a new DocumentSetId with a randomly generated UUID.
     */
    public static DocumentSetId generate() {
        return new DocumentSetId(UUID.randomUUID());
    }

    /**
     * Creates a DocumentSetId from a string representation.
     */
    public static DocumentSetId fromString(String value) {
        return new DocumentSetId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
