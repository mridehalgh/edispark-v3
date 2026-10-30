package com.edispark.mft.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TransferId(UUID value) {
    public TransferId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TransferId generate() {
        return new TransferId(UUID.randomUUID());
    }

    public static TransferId fromString(String value) {
        return new TransferId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
