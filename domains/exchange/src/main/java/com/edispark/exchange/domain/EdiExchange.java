package com.edispark.exchange.domain;

import com.edispark.partnerships.domain.model.ExchangeDirection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class EdiExchange {
    private final UUID id;
    private final String tenantId;
    private final ExchangeDirection direction;
    private final UUID partnerId;
    private UUID agreementId;
    private int agreementRevision;
    private UUID capabilityId;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final List<ExchangeStage> stages;
    private final int messageOrdinal;
    private final String sourceMessageReference;
    private DocumentReference sourceDocument;
    private DocumentReference generatedDocument;
    private UUID mftTransferId;
    private ExchangeStatus status;
    private Instant updatedAt;

    private EdiExchange(UUID id, String tenantId, ExchangeDirection direction, UUID partnerId, UUID agreementId,
            int agreementRevision, UUID capabilityId, String idempotencyKey, int messageOrdinal,
            String sourceMessageReference, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.direction = Objects.requireNonNull(direction);
        this.partnerId = Objects.requireNonNull(partnerId);
        this.agreementId = agreementId;
        if (agreementId != null && agreementRevision < 1) {
            throw new IllegalArgumentException("agreementRevision must be positive");
        }
        this.agreementRevision = agreementRevision;
        this.capabilityId = capabilityId;
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.createdAt = Objects.requireNonNull(now);
        this.messageOrdinal = messageOrdinal < 1 ? 1 : messageOrdinal;
        this.sourceMessageReference = requireText(sourceMessageReference, "sourceMessageReference");
        this.updatedAt = now;
        this.status = ExchangeStatus.RECEIVED;
        this.stages = new ArrayList<>();
        stages.add(new ExchangeStage(status, now, "Exchange accepted"));
    }

    public static EdiExchange outbound(String tenantId, UUID partnerId, UUID agreementId, int agreementRevision,
                                       UUID capabilityId, String idempotencyKey, DocumentReference source, Instant now) {
        EdiExchange exchange = new EdiExchange(UUID.randomUUID(), tenantId, ExchangeDirection.OUTBOUND, partnerId,
                agreementId, agreementRevision, capabilityId, idempotencyKey, 1, "outbound", now);
        exchange.sourceDocument = Objects.requireNonNull(source);
        return exchange;
    }

    public static EdiExchange inbound(String tenantId, UUID partnerId, UUID agreementId, int agreementRevision,
            UUID capabilityId, UUID mftTransferId, DocumentReference retainedSource,
            DocumentReference canonicalDocument, Instant now) {
        return inbound(tenantId, partnerId, agreementId, agreementRevision, capabilityId, mftTransferId,
                retainedSource, canonicalDocument, 1, "1", now);
    }

    public static EdiExchange inbound(String tenantId, UUID partnerId, UUID agreementId, int agreementRevision,
            UUID capabilityId, UUID mftTransferId, DocumentReference retainedSource,
            DocumentReference canonicalDocument, int messageOrdinal, String sourceMessageReference, Instant now) {
        EdiExchange exchange = new EdiExchange(UUID.randomUUID(), tenantId, ExchangeDirection.INBOUND, partnerId,
                agreementId, agreementRevision, capabilityId, inboundKey(mftTransferId, messageOrdinal),
                messageOrdinal, sourceMessageReference, now);
        exchange.sourceDocument = Objects.requireNonNull(retainedSource);
        exchange.generatedDocument = Objects.requireNonNull(canonicalDocument);
        exchange.mftTransferId = Objects.requireNonNull(mftTransferId);
        exchange.advance(ExchangeStatus.DELIVERED, "Inbound payload stored as a canonical document", now);
        return exchange;
    }

    public static EdiExchange inboundNeedsReview(String tenantId, UUID partnerId, UUID mftTransferId,
            int messageOrdinal, String sourceMessageReference, String reason, Instant now) {
        EdiExchange exchange = new EdiExchange(UUID.randomUUID(), tenantId, ExchangeDirection.INBOUND, partnerId,
                null, 0, null, inboundKey(mftTransferId, messageOrdinal), messageOrdinal,
                sourceMessageReference, now);
        exchange.mftTransferId = Objects.requireNonNull(mftTransferId);
        exchange.advance(ExchangeStatus.NEEDS_REVIEW, requireText(reason, "reason"), now);
        return exchange;
    }

    public static EdiExchange restore(UUID id, String tenantId, ExchangeDirection direction, UUID partnerId,
            UUID agreementId, int agreementRevision, UUID capabilityId, String idempotencyKey,
            DocumentReference sourceDocument, DocumentReference generatedDocument, UUID mftTransferId,
            ExchangeStatus status, Instant createdAt, Instant updatedAt, List<ExchangeStage> stages,
            int messageOrdinal, String sourceMessageReference) {
        EdiExchange exchange = new EdiExchange(id, tenantId, direction, partnerId, agreementId, agreementRevision,
                capabilityId, idempotencyKey, messageOrdinal,
                sourceMessageReference == null ? "1" : sourceMessageReference, createdAt);
        exchange.sourceDocument = sourceDocument;
        exchange.generatedDocument = generatedDocument;
        exchange.mftTransferId = mftTransferId;
        exchange.status = Objects.requireNonNull(status);
        exchange.updatedAt = Objects.requireNonNull(updatedAt);
        exchange.stages.clear();
        exchange.stages.addAll(List.copyOf(stages));
        if (exchange.stages.isEmpty()) throw new IllegalArgumentException("Exchange history must not be empty");
        return exchange;
    }

    public void advance(ExchangeStatus next, String detail, Instant now) {
        if (terminal()) throw new IllegalStateException("A terminal exchange cannot advance");
        status = Objects.requireNonNull(next);
        updatedAt = Objects.requireNonNull(now);
        stages.add(new ExchangeStage(next, now, detail));
    }

    public void generated(DocumentReference document) { generatedDocument = Objects.requireNonNull(document); }
    public void completeInbound(UUID nextAgreementId, int nextAgreementRevision, UUID nextCapabilityId,
            DocumentReference retainedSource, DocumentReference canonicalDocument, Instant now) {
        if (direction != ExchangeDirection.INBOUND || status != ExchangeStatus.NEEDS_REVIEW) {
            throw new IllegalStateException("Only an inbound review can be completed");
        }
        agreementId = Objects.requireNonNull(nextAgreementId);
        if (nextAgreementRevision < 1) throw new IllegalArgumentException("agreementRevision must be positive");
        agreementRevision = nextAgreementRevision;
        capabilityId = Objects.requireNonNull(nextCapabilityId);
        sourceDocument = Objects.requireNonNull(retainedSource);
        generatedDocument = Objects.requireNonNull(canonicalDocument);
        status = ExchangeStatus.DELIVERED;
        updatedAt = Objects.requireNonNull(now);
        stages.add(new ExchangeStage(status, now, "Inbound message reprocessed and stored"));
    }
    public void queued(UUID transferId, Instant now) {
        mftTransferId = Objects.requireNonNull(transferId);
        advance(ExchangeStatus.AWAITING_RECEIPT, "AS2 transfer queued", now);
    }
    public boolean terminal() { return status == ExchangeStatus.DELIVERED || status == ExchangeStatus.NEEDS_REVIEW || status == ExchangeStatus.FAILED; }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public ExchangeDirection direction() { return direction; }
    public UUID partnerId() { return partnerId; }
    public UUID agreementId() { return agreementId; }
    public int agreementRevision() { return agreementRevision; }
    public UUID capabilityId() { return capabilityId; }
    public String idempotencyKey() { return idempotencyKey; }
    public DocumentReference sourceDocument() { return sourceDocument; }
    public DocumentReference generatedDocument() { return generatedDocument; }
    public UUID mftTransferId() { return mftTransferId; }
    public ExchangeStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<ExchangeStage> stages() { return List.copyOf(stages); }
    public int messageOrdinal() { return messageOrdinal; }
    public String sourceMessageReference() { return sourceMessageReference; }

    private static String inboundKey(UUID transferId, int messageOrdinal) {
        return messageOrdinal == 1 ? "mft:" + transferId : "mft:" + transferId + ":message:" + messageOrdinal;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
