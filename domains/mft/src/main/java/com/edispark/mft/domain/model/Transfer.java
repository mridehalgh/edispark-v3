package com.edispark.mft.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Accessors(fluent = true)
public final class Transfer {
    @EqualsAndHashCode.Include
    private final TransferId id;
    private final String tenantId;
    private final UUID partnerId;
    private final TransferDirection direction;
    private final String filename;
    private final String contentType;
    private final String as2MessageId;
    private final String idempotencyKey;
    private final TransferId resendOf;
    private final Instant createdAt;
    private final List<TransferAttempt> attempts = new ArrayList<>();
    private TransferStatus status;
    private PayloadRef payload;
    private String mdnDisposition;
    private String failureReason;
    private Instant updatedAt;
    private Instant nextAttemptAt;

    private Transfer(TransferId id, String tenantId, UUID partnerId, TransferDirection direction,
                     String filename, String contentType, String as2MessageId, String idempotencyKey,
                     TransferId resendOf, TransferStatus status, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.partnerId = Objects.requireNonNull(partnerId);
        this.direction = Objects.requireNonNull(direction);
        this.filename = requireText(filename, "filename");
        this.contentType = requireText(contentType, "contentType");
        this.as2MessageId = requireText(as2MessageId, "as2MessageId");
        this.idempotencyKey = idempotencyKey;
        this.resendOf = resendOf;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
        this.nextAttemptAt = now;
    }

    public static Transfer outbound(String tenantId, UUID partnerId, String filename, String contentType,
                                    String as2MessageId, String idempotencyKey, TransferId resendOf, Instant now) {
        requireText(idempotencyKey, "idempotencyKey");
        return new Transfer(TransferId.generate(), tenantId, partnerId, TransferDirection.OUTBOUND,
                filename, contentType, as2MessageId, idempotencyKey, resendOf, TransferStatus.QUEUED, now);
    }

    public static Transfer inbound(String tenantId, UUID partnerId, String filename, String contentType,
                                   String as2MessageId, Instant now) {
        return new Transfer(TransferId.generate(), tenantId, partnerId, TransferDirection.INBOUND,
                filename, contentType, as2MessageId, null, null, TransferStatus.RECEIVED, now);
    }

    public static Transfer restore(TransferId id, String tenantId, UUID partnerId, TransferDirection direction,
                                   String filename, String contentType, String as2MessageId, String idempotencyKey,
                                   TransferId resendOf, TransferStatus status, Instant createdAt, Instant updatedAt,
                                   Instant nextAttemptAt, PayloadRef payload, String mdnDisposition,
                                   String failureReason, List<TransferAttempt> attempts) {
        Transfer transfer = new Transfer(id, tenantId, partnerId, direction, filename, contentType, as2MessageId,
                idempotencyKey, resendOf, status, createdAt);
        transfer.updatedAt = Objects.requireNonNull(updatedAt);
        transfer.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
        transfer.payload = payload;
        transfer.mdnDisposition = mdnDisposition;
        transfer.failureReason = failureReason;
        transfer.attempts.addAll(attempts == null ? List.of() : attempts);
        return transfer;
    }

    public void attachPayload(PayloadRef payload, Instant now) {
        if (this.payload != null) throw new IllegalStateException("payload is immutable once attached");
        this.payload = Objects.requireNonNull(payload);
        this.updatedAt = now;
    }

    public void startAttempt(Instant now) {
        requireStatus(TransferStatus.QUEUED, TransferStatus.RETRY_SCHEDULED);
        status = TransferStatus.PROCESSING;
        attempts.add(new TransferAttempt(attempts.size() + 1, now, null, null, "STARTED", null));
        updatedAt = now;
    }

    public void awaitMdn(Instant now) {
        requireStatus(TransferStatus.PROCESSING);
        status = TransferStatus.AWAITING_MDN;
        updatedAt = now;
    }

    public void deliver(String disposition, Instant now) {
        requireStatus(TransferStatus.PROCESSING, TransferStatus.AWAITING_MDN, TransferStatus.RECEIVED);
        status = TransferStatus.DELIVERED;
        mdnDisposition = requireText(disposition, "disposition");
        updatedAt = now;
    }

    public void scheduleRetry(String reason, Instant nextAttemptAt, Instant now) {
        requireStatus(TransferStatus.PROCESSING, TransferStatus.AWAITING_MDN);
        status = TransferStatus.RETRY_SCHEDULED;
        failureReason = requireText(reason, "reason");
        if (nextAttemptAt.isBefore(now)) throw new IllegalArgumentException("next attempt must not be in the past");
        this.nextAttemptAt = nextAttemptAt;
        updatedAt = now;
    }

    public void requireReview(String reason, Instant now) {
        if (status == TransferStatus.DELIVERED) throw new IllegalStateException("delivered transfers are terminal");
        status = TransferStatus.NEEDS_REVIEW;
        failureReason = requireText(reason, "reason");
        updatedAt = now;
    }

    public List<TransferAttempt> attempts() {
        return List.copyOf(attempts);
    }

    private void requireStatus(TransferStatus... allowed) {
        for (TransferStatus candidate : allowed) if (status == candidate) return;
        throw new IllegalStateException("transition is not allowed from " + status);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
