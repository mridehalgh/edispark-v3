package com.edispark.documents.application.inbound;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.ContentRef;
import com.edispark.documents.domain.model.Derivative;
import com.edispark.documents.domain.model.DerivativeId;
import com.edispark.documents.domain.model.Document;
import com.edispark.documents.domain.model.DocumentId;
import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.model.DocumentSetId;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.documents.domain.model.DocumentVersion;
import com.edispark.documents.domain.model.DocumentVersionId;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.model.SchemaId;
import com.edispark.documents.domain.model.SchemaVersionRef;
import com.edispark.documents.domain.model.TransformationMethod;
import com.edispark.documents.domain.model.VersionIdentifier;
import com.edispark.documents.domain.repository.ContentStore;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stores one retained inbound EDI message and its canonical JSON representation exactly once. */
public final class InboundCanonicalDocumentService {
    private static final SchemaVersionRef SOURCE_SCHEMA = SchemaVersionRef.of(
            SchemaId.fromString("00000000-0000-4000-8000-000000000001"), VersionIdentifier.of("source"));
    private static final String CREATED_BY = "integration:mft-inbound";
    private final DocumentSetRepository sets;
    private final ContentStore content;

    public InboundCanonicalDocumentService(DocumentSetRepository sets, ContentStore content) {
        this.sets = sets;
        this.content = content;
    }

    public Result ingest(InboundRequest command) {
        validate(command);
        Content raw = Content.of(command.rawEdi(), Format.EDI);
        Content canonical = Content.of(command.canonicalJson(), Format.JSON);
        DocumentSetId setId = new DocumentSetId(stable("document-set", command.transferId(), command.messageOrdinal()));
        var existing = sets.findById(setId);
        if (existing.isPresent()) {
            Document document = existing.get().getAllDocuments().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Inbound document set is incomplete"));
            Derivative derivative = document.derivatives().stream()
                    .filter(item -> item.targetFormat() == Format.JSON).findFirst().orElseThrow();
            if (!document.getCurrentVersion().contentHash().equals(raw.hash())
                    || !derivative.contentHash().equals(canonical.hash())) {
                throw new IllegalStateException("MFT transfer is already linked to different document content");
            }
            return result(existing.get(), document);
        }

        content.store(raw);
        content.store(canonical);

        DocumentId documentId = new DocumentId(stable("document", command.transferId(), command.messageOrdinal()));
        DocumentVersionId versionId = new DocumentVersionId(stable(
                "source-version", command.transferId(), command.messageOrdinal()));
        DerivativeId derivativeId = new DerivativeId(stable(
                "canonical-derivative", command.transferId(), command.messageOrdinal()));
        DocumentVersion source = DocumentVersion.reconstitute(versionId, 1, ContentRef.of(raw.hash()), raw.hash(),
                Format.EDI, command.receivedAt(), CREATED_BY, null, "PARSED", command.messageType(), List.of());
        Derivative derivative = Derivative.reconstitute(derivativeId, versionId, Format.JSON,
                ContentRef.of(canonical.hash()), canonical.hash(), TransformationMethod.PROGRAMMATIC,
                "canonical", command.receivedAt());
        Document document = Document.reconstitute(documentId, command.documentType(), SOURCE_SCHEMA,
                List.of(source), List.of(derivative), null);
        DocumentSet documentSet = DocumentSet.reconstitute(setId, Map.of(documentId, document),
                command.receivedAt(), CREATED_BY, Map.ofEntries(
                        Map.entry("direction", "INBOUND"),
                        Map.entry("mftTransferId", command.transferId().toString()),
                        Map.entry("mftPartnerId", command.partnerId().toString()),
                        Map.entry("as2MessageId", command.messageId()),
                        Map.entry("filename", command.filename()),
                        Map.entry("ediStandard", command.standard()),
                        Map.entry("ediMessageType", command.messageType()),
                        Map.entry("messageOrdinal", Integer.toString(command.messageOrdinal())),
                        Map.entry("messageCount", Integer.toString(command.messageCount())),
                        Map.entry("sourceMessageReference", command.sourceMessageReference())));
        sets.save(documentSet);
        return result(documentSet, document);
    }

    private Result result(DocumentSet documentSet, Document document) {
        Derivative canonical = document.derivatives().stream().filter(item -> item.targetFormat() == Format.JSON)
                .findFirst().orElseThrow();
        return new Result(documentSet.id().value(), document.id().value(),
                document.getCurrentVersion().id().value(), document.getCurrentVersion().contentHash().hash(),
                canonical.id().value(), canonical.contentHash().hash());
    }

    private static UUID stable(String kind, UUID transferId, int messageOrdinal) {
        String identity = messageOrdinal == 1 ? kind + ":" + transferId
                : kind + ":" + transferId + ":message:" + messageOrdinal;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private void validate(InboundRequest request) {
        if (request == null || request.transferId() == null || request.partnerId() == null
                || request.documentType() == null || request.receivedAt() == null
                || invalidText(request.messageId(), 255) || invalidText(request.filename(), 255)
                || invalidText(request.standard(), 32) || invalidText(request.messageType(), 32)
                || invalidText(request.sourceMessageReference(), 120)
                || request.messageOrdinal() < 1 || request.messageCount() < request.messageOrdinal()
                || request.rawEdi() == null || request.rawEdi().length == 0
                || request.rawEdi().length > 25 * 1024 * 1024
                || request.canonicalJson() == null || request.canonicalJson().length == 0
                || request.canonicalJson().length > 25 * 1024 * 1024) {
            throw new IllegalArgumentException("Inbound canonical document request is invalid");
        }
    }

    private boolean invalidText(String value, int maximumLength) {
        return value == null || value.isBlank() || value.length() > maximumLength;
    }

    public record InboundRequest(UUID transferId, UUID partnerId, String messageId, String filename, String standard,
                          String messageType, DocumentType documentType, Instant receivedAt,
                          byte[] rawEdi, byte[] canonicalJson, int messageOrdinal, int messageCount,
                          String sourceMessageReference) {
        public InboundRequest(UUID transferId, UUID partnerId, String messageId, String filename, String standard,
                String messageType, DocumentType documentType, Instant receivedAt,
                byte[] rawEdi, byte[] canonicalJson) {
            this(transferId, partnerId, messageId, filename, standard, messageType, documentType, receivedAt,
                    rawEdi, canonicalJson, 1, 1, "1");
        }
    }
    public record Result(UUID documentSetId, UUID documentId, UUID sourceVersionId, String sourceSha256,
                         UUID canonicalDerivativeId, String canonicalSha256) { }
}
