package com.edispark.mft.domain.model;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MdnDelivery(UUID id, String tenantId, UUID partnerId, URI callback, String contentType,
                          String as2From, String as2To, String messageId, String originalMessageId,
                          PayloadRef payload, MdnDeliveryStatus status, int attempts, Instant nextAttemptAt,
                          String lastFailure, Instant createdAt, Instant updatedAt) {
    public static final int MAX_ATTEMPTS = 8;

    public MdnDelivery {
        Objects.requireNonNull(id);
        Objects.requireNonNull(partnerId);
        Objects.requireNonNull(callback);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(status);
        Objects.requireNonNull(nextAttemptAt);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (attempts < 0) throw new IllegalArgumentException("attempts must not be negative");
    }

    public static MdnDelivery pending(UUID id, String tenantId, UUID partnerId, URI callback,
                                      String contentType, String as2From, String as2To, String messageId,
                                      String originalMessageId, PayloadRef payload, Instant now) {
        return new MdnDelivery(id, tenantId, partnerId, callback, contentType, as2From, as2To, messageId,
                originalMessageId, payload, MdnDeliveryStatus.PENDING, 0, now, null, now, now);
    }

    public MdnDelivery delivered(Instant now) {
        return new MdnDelivery(id, tenantId, partnerId, callback, contentType, as2From, as2To, messageId,
                originalMessageId, payload, MdnDeliveryStatus.DELIVERED, attempts + 1, now, null, createdAt, now);
    }

    public MdnDelivery failedAttempt(String reason, boolean retryable, Instant now) {
        int nextAttempts = attempts + 1;
        boolean exhausted = !retryable || nextAttempts >= MAX_ATTEMPTS;
        long delaySeconds = Math.min(900, 5L << Math.min(nextAttempts - 1, 8));
        return new MdnDelivery(id, tenantId, partnerId, callback, contentType, as2From, as2To, messageId,
                originalMessageId, payload, exhausted ? MdnDeliveryStatus.FAILED : MdnDeliveryStatus.PENDING,
                nextAttempts, exhausted ? now : now.plus(Duration.ofSeconds(delaySeconds)), safe(reason),
                createdAt, now);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "MDN delivery failed";
        return value.substring(0, Math.min(500, value.length()));
    }
}
