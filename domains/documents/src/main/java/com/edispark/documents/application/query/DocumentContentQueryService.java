package com.edispark.documents.application.query;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.edispark.documents.application.handler.ContentNotFoundException;
import com.edispark.documents.application.handler.DerivativeNotFoundException;
import com.edispark.documents.application.handler.DocumentNotFoundException;
import com.edispark.documents.application.handler.DocumentSetNotFoundException;
import com.edispark.documents.application.handler.VersionNotFoundException;
import com.edispark.documents.domain.model.Derivative;
import com.edispark.documents.domain.model.DerivativeId;
import com.edispark.documents.domain.model.Document;
import com.edispark.documents.domain.model.ContentHash;
import com.edispark.documents.domain.model.DocumentId;
import com.edispark.documents.domain.model.DocumentSet;
import com.edispark.documents.domain.model.DocumentSetId;
import com.edispark.documents.domain.model.DocumentVersion;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.repository.ContentStore;
import com.edispark.documents.domain.repository.DocumentSetRepository;

public class DocumentContentQueryService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentContentQueryService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_REFERENCE_CONTENT_BYTES = 1024 * 1024;

    private final DocumentSetRepository documentSetRepository;
    private final ContentStore contentStore;

    public DocumentContentQueryService(DocumentSetRepository documentSetRepository, ContentStore contentStore) {
        this.documentSetRepository = Objects.requireNonNull(documentSetRepository);
        this.contentStore = Objects.requireNonNull(contentStore);
    }

    public RetrievedContent getVersionContent(DocumentSetId setId, DocumentId documentId, int versionNumber) {
        DocumentSet documentSet = documentSetRepository.findById(setId)
                .orElseThrow(() -> new DocumentSetNotFoundException(setId));
        var document = documentSet.getDocument(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(setId, documentId));
        DocumentVersion version = document.getVersion(versionNumber)
                .orElseThrow(() -> new VersionNotFoundException(documentId, versionNumber));
        byte[] bytes = contentStore.retrieve(version.contentHash())
                .orElseThrow(() -> new ContentNotFoundException(version.contentHash()));

        return new RetrievedContent(
                bytes,
                version.format(),
                version.contentHash().toFullString(),
                contentTypeFor(version.format()),
                fileNameFor(documentSet, documentId, versionNumber, version.format()));
    }

    public Optional<CanonicalRepresentation> currentRepresentation(Document document) {
        var version = document.getCurrentVersion();
        if (version.format() == Format.JSON) {
            return Optional.of(new CanonicalRepresentation(version.id().value(), version.contentHash()));
        }
        return document.derivatives().stream()
                .filter(item -> item.targetFormat() == Format.JSON && item.sourceVersionId().equals(version.id()))
                .filter(item -> item.variantKey().equals("canonical") || item.variantKey().equals("default"))
                .findFirst().map(item -> new CanonicalRepresentation(item.id().value(), item.contentHash()));
    }

    public Optional<String> businessReference(Document document) {
        var representation = currentRepresentation(document);
        if (representation.isEmpty()) return Optional.empty();
        return contentStore.retrieve(representation.get().hash()).flatMap(bytes -> readBusinessReference(document.id(), bytes));
    }

    public CanonicalDocument readPinnedCanonical(RepresentationPin pin) {
        var set = documentSetRepository.findById(pin.setId())
                .orElseThrow(() -> new DocumentNotFoundException(pin.setId(), pin.documentId()));
        var document = set.getDocument(pin.documentId())
                .orElseThrow(() -> new DocumentNotFoundException(pin.setId(), pin.documentId()));
        var current = currentRepresentation(document);
        if (current.isEmpty()) throw new IllegalArgumentException("Document has no current canonical representation");
        var representation = current.get();
        if (!representation.id().equals(pin.representationId()) || !representation.hash().toFullString().equals(pin.contentHash())) {
            throw new CanonicalSourceChanged();
        }
        var bytes = contentStore.retrieve(representation.hash()).orElseThrow(() -> new ContentNotFoundException(representation.hash()));
        String partnerId = set.metadata().get("partnerId");
        return new CanonicalDocument(document.type(), partnerId == null ? null : UUID.fromString(partnerId), bytes);
    }

    private static Optional<String> readBusinessReference(DocumentId documentId, byte[] json) {
        if (json.length > MAX_REFERENCE_CONTENT_BYTES) return Optional.empty();
        try {
            var root = JSON.readTree(json);
            if (root == null || !root.isObject()) return Optional.empty();
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getKey().startsWith("_")) continue;
                var id = entry.getValue().path(0).path("ID").path(0).path("_");
                if (id.isTextual()) return Optional.of(id.asText());
            }
            return Optional.empty();
        } catch (java.io.IOException malformedRepresentation) {
            log.debug("Canonical document reference unavailable documentId={} reason={}",
                    documentId.value(), malformedRepresentation.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public record CanonicalRepresentation(UUID id, ContentHash hash) { }
    public record RepresentationPin(DocumentSetId setId, DocumentId documentId, UUID representationId, String contentHash) { }
    public record CanonicalDocument(DocumentType type, UUID partnerId, byte[] content) {
        public CanonicalDocument { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }

    public static final class CanonicalSourceChanged extends IllegalArgumentException {
        public CanonicalSourceChanged() { super("The canonical representation changed"); }
    }

    public RetrievedContent getDerivativeContent(
            DocumentSetId setId,
            DocumentId documentId,
            DerivativeId derivativeId) {
        DocumentSet documentSet = documentSetRepository.findById(setId)
                .orElseThrow(() -> new DocumentSetNotFoundException(setId));
        var document = documentSet.getDocument(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(setId, documentId));
        Derivative derivative = document.getDerivative(derivativeId)
                .orElseThrow(() -> new DerivativeNotFoundException(documentId, derivativeId));
        byte[] bytes = contentStore.retrieve(derivative.contentHash())
                .orElseThrow(() -> new ContentNotFoundException(derivative.contentHash()));

        return new RetrievedContent(
                bytes,
                derivative.targetFormat(),
                derivative.contentHash().toFullString(),
                contentTypeFor(derivative.targetFormat()),
                derivativeFileNameFor(documentId, derivative));
    }

    private String contentTypeFor(Format format) {
        return switch (format) {
            case XML -> "application/xml";
            case JSON -> "application/json";
            case PDF -> "application/pdf";
            case ZPL -> "application/vnd.zebra-zpl";
            case EDI -> "application/edi";
        };
    }

    private String fileNameFor(DocumentSet documentSet, DocumentId documentId, int versionNumber, Format format) {
        String sourceFileName = documentSet.metadata().get("sourceFileName");
        if (sourceFileName != null && !sourceFileName.isBlank()) {
            return sourceFileName;
        }
        return documentId.value() + "-v" + versionNumber + extensionFor(format);
    }

    private String derivativeFileNameFor(DocumentId documentId, Derivative derivative) {
        return documentId.value() + "-derivative-" + derivative.id().value() + extensionFor(derivative.targetFormat());
    }

    private String extensionFor(Format format) {
        return switch (format) {
            case XML -> ".xml";
            case JSON -> ".json";
            case PDF -> ".pdf";
            case ZPL -> ".zpl";
            case EDI -> ".edi";
        };
    }
}
