package com.edispark.partnerships.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ExchangeCapability(UUID id, ExchangeDirection direction, BusinessDocumentType documentType,
        EdiStandard standard, String release, String messageType, UUID sourceSchemaId, UUID targetSchemaId,
        UUID mappingRevisionId, UUID as2ConnectionId, AcknowledgementPolicy acknowledgementPolicy,
        BatchingPolicy batchingPolicy, OutboundLossPolicy outboundLossPolicy) {
    public ExchangeCapability {
        Objects.requireNonNull(id);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(documentType);
        Objects.requireNonNull(standard);
        requireText(release, "release");
        requireText(messageType, "messageType");
        Objects.requireNonNull(sourceSchemaId);
        Objects.requireNonNull(targetSchemaId);
        Objects.requireNonNull(mappingRevisionId);
        Objects.requireNonNull(as2ConnectionId);
        Objects.requireNonNull(acknowledgementPolicy);
        Objects.requireNonNull(batchingPolicy);
        outboundLossPolicy = outboundLossPolicy == null ? OutboundLossPolicy.STRICT : outboundLossPolicy;
    }

    /** Compatibility constructor for persisted and source callers created before loss policy was configurable. */
    public ExchangeCapability(UUID id, ExchangeDirection direction, BusinessDocumentType documentType,
            EdiStandard standard, String release, String messageType, UUID sourceSchemaId, UUID targetSchemaId,
            UUID mappingRevisionId, UUID as2ConnectionId, AcknowledgementPolicy acknowledgementPolicy,
            BatchingPolicy batchingPolicy) {
        this(id, direction, documentType, standard, release, messageType, sourceSchemaId, targetSchemaId,
                mappingRevisionId, as2ConnectionId, acknowledgementPolicy, batchingPolicy, OutboundLossPolicy.STRICT);
    }

    public String routingKey() {
        return direction + ":" + standard + ":" + release + ":" + messageType;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
