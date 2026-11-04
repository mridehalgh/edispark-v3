package com.edispark.wireup.modules;

import com.edispark.exchange.application.TransportAcl;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.TransferProvenance;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class InProcessTransportAcl implements TransportAcl {
    private final MftService mft;

    InProcessTransportAcl(MftService mft) {
        this.mft = mft;
    }

    @Override
    public UUID submit(UUID connectionId, String idempotencyKey, String filename, String contentType,
            byte[] content, UUID exchangeId, UUID agreementId, int agreementRevision, UUID capabilityId,
            DocumentReference sourceDocument, UUID mappingRevisionId) {
        TransferProvenance provenance = new TransferProvenance(exchangeId, agreementId, agreementRevision,
                capabilityId, sourceDocument.documentSetId(), sourceDocument.documentId(), mappingRevisionId);
        try {
            return mft.submitOutbound(new MftService.SubmitOutbound(connectionId, idempotencyKey, filename,
                    contentType, new ByteArrayInputStream(content), null, provenance)).id().value();
        } catch (IOException exception) {
            throw new IllegalStateException("MFT could not retain the outbound payload", exception);
        }
    }
}
