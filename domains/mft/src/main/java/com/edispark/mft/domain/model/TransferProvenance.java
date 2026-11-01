package com.edispark.mft.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TransferProvenance(UUID exchangeId, UUID agreementId, int agreementRevision, UUID capabilityId,
        UUID documentSetId, UUID documentId, UUID mappingRevisionId) {
    public TransferProvenance {
        Objects.requireNonNull(exchangeId);
        Objects.requireNonNull(agreementId);
        if (agreementRevision < 1) throw new IllegalArgumentException("agreementRevision must be positive");
        Objects.requireNonNull(capabilityId);
        Objects.requireNonNull(documentSetId);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(mappingRevisionId);
    }
}
