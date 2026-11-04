package com.edispark.wireup.modules;

import com.edispark.documents.application.inbound.InboundCanonicalDocumentService;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.integrationapplication.inbound.InboundMftPayloadEvent;
import com.edispark.integrationapplication.inbound.InboundModuleAccess;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferDirection;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.PayloadStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class InProcessInboundModuleAccess implements InboundModuleAccess {
    private final MftRepository transfers;
    private final PayloadStore payloads;
    private final InboundCanonicalDocumentService documents;

    InProcessInboundModuleAccess(MftRepository transfers, PayloadStore payloads,
            InboundCanonicalDocumentService documents) {
        this.transfers = transfers;
        this.payloads = payloads;
        this.documents = documents;
    }

    @Override
    public byte[] payload(InboundMftPayloadEvent event) {
        Transfer transfer = transfers.findTransfer(event.tenantId(), new TransferId(event.transferId()))
                .orElseThrow(() -> new IllegalArgumentException("Retained MFT transfer was not found"));
        try (var input = payloads.get(event.tenantId(), transfer.payload())) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Retained MFT payload could not be read", exception);
        }
    }

    @Override
    public List<InboundMftPayloadEvent> inboundTransfers(String tenantId) {
        return transfers.listTransfers(tenantId).stream()
                .filter(transfer -> transfer.direction() == TransferDirection.INBOUND && transfer.payload() != null)
                .map(this::event).toList();
    }

    @Override
    public StoredDocument store(InboundDocument document) {
        var result = documents.ingest(new InboundCanonicalDocumentService.InboundRequest(
                document.transferId(), document.partnerId(), document.messageId(), document.filename(),
                document.standard(), document.messageType(), DocumentType.valueOf(document.documentType().name()),
                document.receivedAt(), document.rawEdi(), document.canonicalJson(), document.messageOrdinal(),
                document.messageCount(), document.sourceMessageReference()));
        return new StoredDocument(result.documentSetId(), result.documentId(), result.sourceVersionId(),
                result.sourceSha256(), result.canonicalDerivativeId(), result.canonicalSha256());
    }

    private InboundMftPayloadEvent event(Transfer transfer) {
        UUID eventId = UUID.nameUUIDFromBytes((transfer.tenantId() + ":inbound:" + transfer.id())
                .getBytes(StandardCharsets.UTF_8));
        return new InboundMftPayloadEvent(eventId, transfer.tenantId(), transfer.id().value(), transfer.partnerId(),
                transfer.as2MessageId(), transfer.filename(), transfer.contentType(), transfer.payload().sha256(),
                transfer.payload().size(), transfer.createdAt());
    }
}
