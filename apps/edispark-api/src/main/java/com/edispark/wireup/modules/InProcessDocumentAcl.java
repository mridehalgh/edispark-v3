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
import com.edispark.documents.domain.model.DocumentVersion;
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
        Representation representation = canonical(document);
        return new DocumentDescriptor(reference(setId, documentId, representation),
                BusinessDocumentType.valueOf(document.type().name()), "UBL_2_1_JSON", documentId.toString());
    }

    @Override
    public byte[] content(DocumentReference reference) {
        Document document = document(reference.documentSetId(), reference.documentId());
        Representation representation = canonical(document);
        if (!reference.equals(reference(reference.documentSetId(), reference.documentId(), representation))) {
            throw new IllegalArgumentException("Document source changed before exchange processing");
        }
        if (representation.derivative() != null) {
            return content.getDerivativeContent(new DocumentSetId(reference.documentSetId()),
                    new DocumentId(reference.documentId()), representation.derivative().id()).bytes();
        }
        return content.getVersionContent(new DocumentSetId(reference.documentSetId()),
                new DocumentId(reference.documentId()), representation.version().versionNumber()).bytes();
    }

    @Override
    public DocumentReference retainDerivative(DocumentReference source, String format, byte[] bytes,
            UUID mappingRevisionId, UUID exchangeId) {
        Document document = document(source.documentSetId(), source.documentId());
        Derivative derivative = commands.handle(new StoreExternalDerivativeCommand(
                new DocumentSetId(source.documentSetId()), new DocumentId(source.documentId()),
                document.getCurrentVersion().versionNumber(), Content.of(bytes, Format.valueOf(format)),
                mappingRevisionId.toString()));
        return new DocumentReference(source.documentSetId(), source.documentId(), derivative.id().value(),
                derivative.contentHash().toFullString());
    }

    private Document document(UUID setId, UUID documentId) {
        return sets.findById(new DocumentSetId(setId))
                .flatMap(set -> set.getDocument(new DocumentId(documentId)))
                .orElseThrow(() -> new IllegalArgumentException("Document was not returned"));
    }

    private Representation canonical(Document document) {
        DocumentVersion version = document.getCurrentVersion();
        if (version.format() == Format.JSON) return new Representation(version, null);
        Derivative derivative = document.derivatives().stream()
                .filter(item -> item.targetFormat() == Format.JSON).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document has no canonical JSON representation"));
        return new Representation(version, derivative);
    }

    private DocumentReference reference(UUID setId, UUID documentId, Representation representation) {
        UUID id = representation.derivative() == null
                ? representation.version().id().value() : representation.derivative().id().value();
        String hash = representation.derivative() == null
                ? representation.version().contentHash().toFullString()
                : representation.derivative().contentHash().toFullString();
        return new DocumentReference(setId, documentId, id, hash);
    }

    private record Representation(DocumentVersion version, Derivative derivative) { }
}
