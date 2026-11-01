package com.edispark.partnerships.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ExchangeCapability(UUID id, ExchangeDirection direction, BusinessDocumentType documentType,
        EdiStandard standard, String release, String messageType, UUID sourceSchemaId, UUID targetSchemaId,
        UUID mappingRevisionId, UUID as2ConnectionId, AcknowledgementPolicy acknowledgementPolicy,
        BatchingPolicy batchingPolicy) {
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
    }

    public String routingKey() {
        return direction + ":" + standard + ":" + release + ":" + messageType;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
