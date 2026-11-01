package com.edispark.exchange.application;

import com.edispark.exchange.domain.DocumentReference;
import java.util.UUID;

public interface TransportAcl {
    UUID submit(UUID as2ConnectionId, String idempotencyKey, String filename, String contentType, byte[] content,
                UUID exchangeId, UUID agreementId, int agreementRevision, UUID capabilityId,
                DocumentReference sourceDocument, UUID mappingRevisionId);
}
