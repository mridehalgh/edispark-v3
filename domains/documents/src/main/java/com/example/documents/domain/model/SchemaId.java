package com.example.documents.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a Schema aggregate.
 */
public record SchemaId(UUID value) {

    public SchemaId {
        Objects.requireNonNull(value, "SchemaId value cannot be null");
    }

    /**
     * Creates a new SchemaId with a randomly generated UUID.
     */
    public static SchemaId generate() {
        return new SchemaId(UUID.randomUUID());
    }

    /**
     * Creates a SchemaId from a string representation.
     */
    public static SchemaId fromString(String value) {
        return new SchemaId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
