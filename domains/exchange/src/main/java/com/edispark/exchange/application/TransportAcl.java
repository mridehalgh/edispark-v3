package com.edispark.exchange.application;

import com.edispark.exchange.domain.DocumentReference;
import java.util.UUID;

public interface TransportAcl {
    UUID submit(UUID as2ConnectionId, String idempotencyKey, String filename, String contentType, byte[] content,
                UUID exchangeId, UUID agreementId, int agreementRevision, UUID capabilityId,
                DocumentReference sourceDocument, UUID mappingRevisionId);

    default Receipt receipt(UUID transferId, UUID exchangeId) { return new Receipt(Outcome.PENDING, null, ""); }
    enum Outcome { PENDING, DELIVERED, NEEDS_REVIEW, FAILED }
    record Receipt(Outcome outcome, java.time.Instant recordedAt, String detail) { }
}
