package com.example.documents.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a Derivative entity.
 */
public record DerivativeId(UUID value) {

    public DerivativeId {
        Objects.requireNonNull(value, "DerivativeId value cannot be null");
    }

    /**
     * Creates a new DerivativeId with a randomly generated UUID.
     */
    public static DerivativeId generate() {
        return new DerivativeId(UUID.randomUUID());
    }

    /**
     * Creates a DerivativeId from a string representation.
     */
    public static DerivativeId fromString(String value) {
        return new DerivativeId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
