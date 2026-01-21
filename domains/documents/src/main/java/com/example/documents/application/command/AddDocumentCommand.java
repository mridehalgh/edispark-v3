package com.example.documents.application.command;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.SchemaVersionRef;

import java.util.Objects;

/**
 * Command to add a new document to an existing DocumentSet.
 *
 * @param documentSetId the identifier of the document set to add to
 * @param type the type of the document
 * @param schemaRef reference to the schema version for validation
 * @param content the document content
 * @param createdBy identifier of the user adding the document
 * @param relatedDocumentId optional reference to a related document in the same set
 */
public record AddDocumentCommand(
        DocumentSetId documentSetId,
        DocumentType type,
        SchemaVersionRef schemaRef,
        Content content,
        String createdBy,
        DocumentId relatedDocumentId
) {
    public AddDocumentCommand {
        Objects.requireNonNull(documentSetId, "Document set ID cannot be null");
        Objects.requireNonNull(type, "Document type cannot be null");
        Objects.requireNonNull(schemaRef, "Schema reference cannot be null");
        Objects.requireNonNull(content, "Content cannot be null");
        Objects.requireNonNull(createdBy, "Created by cannot be null");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("Created by cannot be blank");
        }
        // relatedDocumentId can be null
    }

    /**
     * Creates a command without a related document reference.
     */
    public static AddDocumentCommand of(
            DocumentSetId documentSetId,
            DocumentType type,
            SchemaVersionRef schemaRef,
            Content content,
            String createdBy) {
        return new AddDocumentCommand(documentSetId, type, schemaRef, content, createdBy, null);
    }

    /**
     * Creates a command with a related document reference.
     */
    public static AddDocumentCommand withRelation(
            DocumentSetId documentSetId,
            DocumentType type,
            SchemaVersionRef schemaRef,
            Content content,
            String createdBy,
            DocumentId relatedDocumentId) {
        return new AddDocumentCommand(documentSetId, type, schemaRef, content, createdBy, relatedDocumentId);
    }
}
