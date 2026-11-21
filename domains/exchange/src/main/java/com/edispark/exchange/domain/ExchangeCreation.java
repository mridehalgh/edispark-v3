package com.edispark.exchange.domain;

import com.edispark.exchange.domain.*;
import java.util.*;
import java.time.Instant;

/** Creates tenant-owned records without exposing tenant selection to business services. */
public interface ExchangeCreation {
    EdiExchange outbound(UUID partnerId, UUID agreementId, int agreementRevision, UUID capabilityId, String idempotencyKey, DocumentReference source, Instant now);
    EdiExchange inbound(UUID partnerId, UUID agreementId, int agreementRevision, UUID capabilityId,
            UUID transferId, DocumentReference source, DocumentReference canonical, int messageOrdinal,
            String messageReference, Instant now);
    EdiExchange inboundNeedsReview(UUID partnerId, UUID transferId, int messageOrdinal,
            String messageReference, String reason, Instant now);
}
