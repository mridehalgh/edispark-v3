package com.edispark.integrationapplication.inbound;

import com.edispark.partnerships.domain.model.BusinessDocumentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** In-process boundary used by inbound orchestration without coupling it to HTTP controllers. */
public interface InboundModuleAccess {
    byte[] payload(InboundMftPayloadEvent event);

    List<InboundMftPayloadEvent> inboundTransfers(String tenantId);

    StoredDocument store(InboundDocument document);

    record InboundDocument(UUID transferId, UUID partnerId, String messageId, String filename,
            String standard, String messageType, BusinessDocumentType documentType, Instant receivedAt,
            byte[] rawEdi, byte[] canonicalJson, int messageOrdinal, int messageCount,
            String sourceMessageReference) { }

    record StoredDocument(UUID documentSetId, UUID documentId, UUID sourceVersionId, String sourceSha256,
            UUID canonicalDerivativeId, String canonicalSha256) { }
}
