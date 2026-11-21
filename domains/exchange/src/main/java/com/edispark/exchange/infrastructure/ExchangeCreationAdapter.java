package com.edispark.exchange.infrastructure;

import com.edispark.exchange.domain.*;
import java.util.*;
import java.time.Instant;
import com.edispark.exchange.domain.ExchangeCreation;
import io.cottn.core.tenancy.TenantAccess;

public interface ExchangeCreationAdapter extends ExchangeCreation {
    default EdiExchange outbound(UUID partnerId, UUID agreementId, int agreementRevision, UUID capabilityId, String idempotencyKey, DocumentReference source, Instant now) { return EdiExchange.outbound(TenantAccess.currentId(), partnerId, agreementId, agreementRevision, capabilityId, idempotencyKey, source, now); }

    @Override
    default EdiExchange inbound(UUID partnerId, UUID agreementId, int agreementRevision, UUID capabilityId,
            UUID transferId, DocumentReference source, DocumentReference canonical, int messageOrdinal,
            String messageReference, Instant now) {
        return EdiExchange.inbound(TenantAccess.currentId(), partnerId, agreementId, agreementRevision,
                capabilityId, transferId, source, canonical, messageOrdinal, messageReference, now);
    }

    @Override
    default EdiExchange inboundNeedsReview(UUID partnerId, UUID transferId, int messageOrdinal,
            String messageReference, String reason, Instant now) {
        return EdiExchange.inboundNeedsReview(TenantAccess.currentId(), partnerId, transferId,
                messageOrdinal, messageReference, reason, now);
    }
}
