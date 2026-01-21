package com.example.documents.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a SchemaVersion entity.
 */
public record SchemaVersionId(UUID value) {

    public SchemaVersionId {
        Objects.requireNonNull(value, "SchemaVersionId value cannot be null");
    }

    /**
     * Creates a new SchemaVersionId with a randomly generated UUID.
     */
    public static SchemaVersionId generate() {
        return new SchemaVersionId(UUID.randomUUID());
    }

    /**
     * Creates a SchemaVersionId from a string representation.
     */
    public static SchemaVersionId fromString(String value) {
        return new SchemaVersionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
