package com.edispark.exchange.domain;

import java.util.Objects;
import java.util.UUID;

public record DocumentReference(UUID documentSetId, UUID documentId, UUID representationId, String contentHash) {
    public DocumentReference {
        Objects.requireNonNull(documentSetId);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(representationId);
        if (contentHash == null || contentHash.isBlank()) throw new IllegalArgumentException("contentHash is required");
    }
}
