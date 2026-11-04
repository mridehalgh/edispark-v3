package com.edispark.partnerships.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SsccReservation(
        UUID id,
        String tenantId,
        UUID issuerId,
        String idempotencyKey,
        String context,
        List<String> ssccs,
        Instant reservedAt,
        Instant expiresAt) {
    public SsccReservation {
        Objects.requireNonNull(id);
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        Objects.requireNonNull(issuerId);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new IllegalArgumentException("idempotencyKey is invalid");
        }
        if (context == null || context.isBlank() || context.length() > 240) throw new IllegalArgumentException("context is invalid");
        ssccs = List.copyOf(ssccs);
        if (ssccs.isEmpty() || ssccs.stream().anyMatch(value -> !SsccNumber.isValid(value))) {
            throw new IllegalArgumentException("reservation must contain valid SSCCs");
        }
        Objects.requireNonNull(reservedAt);
        Objects.requireNonNull(expiresAt);
    }
}
