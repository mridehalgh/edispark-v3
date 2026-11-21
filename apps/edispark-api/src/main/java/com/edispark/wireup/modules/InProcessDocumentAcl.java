package com.edispark.wireup.modules;

import com.edispark.documents.application.command.StoreExternalDerivativeCommand;
import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.application.query.DocumentContentQueryService;
import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.Derivative;
import com.edispark.documents.domain.model.DerivativeId;
import com.edispark.documents.domain.model.Document;
import com.edispark.documents.domain.model.DocumentId;
import com.edispark.documents.domain.model.DocumentSetId;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.exchange.application.DocumentAcl;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class InProcessDocumentAcl implements DocumentAcl {
    private final DocumentSetRepository sets;
    private final DocumentContentQueryService content;
    private final DocumentSetCommandHandler commands;

    InProcessDocumentAcl(DocumentSetRepository sets, DocumentContentQueryService content,
            DocumentSetCommandHandler commands) {
        this.sets = sets;
        this.content = content;
        this.commands = commands;
    }

    @Override
    public DocumentDescriptor describe(UUID setId, UUID documentId) {
        Document document = document(setId, documentId);
        var representation = canonicalReference(setId, documentId, document);
        return new DocumentDescriptor(representation,
                BusinessDocumentType.valueOf(document.type().name()), "UBL_2_1_JSON", documentId.toString(),
                document.lifecycleStatus().name(), metadataId(setId, "partnerId"), metadataId(setId, "mftPartnerId"));
    }

    private UUID metadataId(UUID setId, String field) {
        String value = sets.findById(new DocumentSetId(setId)).orElseThrow().metadata().get(field);
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("The retained partner reference is invalid. Create a corrected draft and select its partner."); }
    }

    @Override public java.util.List<com.edispark.exchange.application.ReadinessIssue> validate(DocumentDescriptor descriptor) {
        return commands.validateCanonical(com.edispark.documents.domain.model.DocumentType.valueOf(descriptor.type().name()),
                content(descriptor.current())).errors().stream().map(error -> com.edispark.exchange.application.ReadinessIssue.document(
                        error.code(), error.path(), error.message())).toList();
    }

    @Override
    public byte[] content(DocumentReference reference) {
        Document document = document(reference.documentSetId(), reference.documentId());
        var current = canonicalReference(reference.documentSetId(), reference.documentId(), document);
        if (!reference.equals(current)) {
            throw new IllegalArgumentException("Document source changed before exchange processing");
        }
        var version = document.getCurrentVersion();
        var setId = new DocumentSetId(reference.documentSetId());
        var documentId = new DocumentId(reference.documentId());
        if (version.id().value().equals(current.representationId())) {
            return content.getVersionContent(setId, documentId, version.versionNumber()).bytes();
        }
        return content.getDerivativeContent(setId, documentId, new DerivativeId(current.representationId())).bytes();
    }

    @Override
    public DocumentReference retainDerivative(DocumentReference source, GeneratedArtifact artifact, byte[] bytes,
            UUID mappingRevisionId, UUID exchangeId) {
        Document document = document(source.documentSetId(), source.documentId());
        content(source);
        Derivative derivative = commands.handle(new StoreExternalDerivativeCommand(
                new DocumentSetId(source.documentSetId()), new DocumentId(source.documentId()),
                document.getCurrentVersion().versionNumber(), Content.of(bytes, storageFormat(artifact)),
                artifact.name().toLowerCase(java.util.Locale.ROOT) + ":" + exchangeId + ":" + mappingRevisionId));
        return new DocumentReference(source.documentSetId(), source.documentId(), derivative.id().value(),
                derivative.contentHash().toFullString());
    }

    static Format storageFormat(GeneratedArtifact artifact) {
        return Format.valueOf(artifact.documentFormat());
    }

    private Document document(UUID setId, UUID documentId) {
        return sets.findById(new DocumentSetId(setId))
                .flatMap(set -> set.getDocument(new DocumentId(documentId)))
                .orElseThrow(() -> new IllegalArgumentException("Document was not returned"));
    }

    private DocumentReference canonicalReference(UUID setId, UUID documentId, Document document) {
        var representation = content.currentRepresentation(document)
                .orElseThrow(() -> new IllegalArgumentException("Document has no canonical JSON representation"));
        return new DocumentReference(setId, documentId, representation.id(), representation.hash().toFullString());
    }
}
