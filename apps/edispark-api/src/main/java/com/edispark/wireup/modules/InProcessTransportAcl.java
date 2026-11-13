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

    @Override public Receipt receipt(UUID transferId, UUID exchangeId) {
        var transfer = mft.getTransfer(new com.edispark.mft.domain.model.TransferId(transferId));
        if (transfer.provenance() == null || !exchangeId.equals(transfer.provenance().exchangeId())) {
            return new Receipt(Outcome.NEEDS_REVIEW, transfer.updatedAt(), "The retained AS2 transfer does not match this exchange. Review its transport evidence.");
        }
        return switch (transfer.status()) {
            case DELIVERED -> transfer.mdnDisposition() != null && transfer.mdnDisposition().matches("(?i).*;\\s*processed\\s*$")
                    ? new Receipt(Outcome.DELIVERED, transfer.updatedAt(), "AS2 MDN verified: processed. Transport receipt does not imply business acceptance.")
                    : new Receipt(Outcome.NEEDS_REVIEW, transfer.updatedAt(), "The retained AS2 delivery has no successful processed MDN. Review the transport receipt.");
            case NEEDS_REVIEW -> new Receipt(Outcome.NEEDS_REVIEW, transfer.updatedAt(), "AS2 delivery requires review. Open the transfer for the recorded receipt and delivery findings.");
            case FAILED -> new Receipt(Outcome.FAILED, transfer.updatedAt(), "AS2 delivery failed. Open the transfer for the recorded delivery findings.");
            default -> new Receipt(Outcome.PENDING, transfer.updatedAt(), "");
        };
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
