package com.example.documents.domain.event;

import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.SchemaVersionRef;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a document is added to a DocumentSet.
 * 
 * <p>Requirements: 9.2, 9.5, 9.6</p>
 *
 * @param documentSetId the identifier of the document set
 * @param documentId the identifier of the added document
 * @param type the type of the document
 * @param schemaRef reference to the schema version the document conforms to
 * @param occurredAt the timestamp when the event occurred
 */
public record DocumentAdded(
        DocumentSetId documentSetId,
        DocumentId documentId,
        DocumentType type,
        SchemaVersionRef schemaRef,
        Instant occurredAt
) implements DomainEvent {

    public DocumentAdded {
        Objects.requireNonNull(documentSetId, "DocumentSetId cannot be null");
        Objects.requireNonNull(documentId, "DocumentId cannot be null");
        Objects.requireNonNull(type, "DocumentType cannot be null");
        Objects.requireNonNull(schemaRef, "SchemaVersionRef cannot be null");
        Objects.requireNonNull(occurredAt, "OccurredAt cannot be null");
    }

    /**
     * Creates a new DocumentAdded event with the current timestamp.
     */
    public static DocumentAdded now(
            DocumentSetId documentSetId,
            DocumentId documentId,
            DocumentType type,
            SchemaVersionRef schemaRef) {
        return new DocumentAdded(documentSetId, documentId, type, schemaRef, Instant.now());
    }
}
