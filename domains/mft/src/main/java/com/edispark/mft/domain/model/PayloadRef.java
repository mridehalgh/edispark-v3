package com.edispark.mft.domain.model;

import java.util.Objects;

public record PayloadRef(String key, String sha256, long size) {
    public PayloadRef {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(sha256, "sha256 must not be null");
        if (size < 0) throw new IllegalArgumentException("size must not be negative");
    }
}
