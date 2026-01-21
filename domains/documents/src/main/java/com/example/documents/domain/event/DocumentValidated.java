package com.example.documents.domain.event;

import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentVersionId;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a document is validated against its schema.
 * 
 * <p>Requirements: 9.4, 9.5, 9.6</p>
 *
 * @param documentSetId the identifier of the document set
 * @param documentId the identifier of the document
 * @param versionId the identifier of the validated version
 * @param valid whether the validation passed
 * @param errorCount the number of validation errors (0 if valid)
 * @param occurredAt the timestamp when the event occurred
 */
public record DocumentValidated(
        DocumentSetId documentSetId,
        DocumentId documentId,
        DocumentVersionId versionId,
        boolean valid,
        int errorCount,
        Instant occurredAt
) implements DomainEvent {

    public DocumentValidated {
        Objects.requireNonNull(documentSetId, "DocumentSetId cannot be null");
        Objects.requireNonNull(documentId, "DocumentId cannot be null");
        Objects.requireNonNull(versionId, "VersionId cannot be null");
        if (errorCount < 0) {
            throw new IllegalArgumentException("Error count cannot be negative");
        }
        if (valid && errorCount > 0) {
            throw new IllegalArgumentException("Valid document cannot have errors");
        }
        Objects.requireNonNull(occurredAt, "OccurredAt cannot be null");
    }

    /**
     * Creates a new DocumentValidated event for a successful validation.
     */
    public static DocumentValidated success(
            DocumentSetId documentSetId,
            DocumentId documentId,
            DocumentVersionId versionId) {
        return new DocumentValidated(documentSetId, documentId, versionId, true, 0, Instant.now());
    }

    /**
     * Creates a new DocumentValidated event for a failed validation.
     */
    public static DocumentValidated failure(
            DocumentSetId documentSetId,
            DocumentId documentId,
            DocumentVersionId versionId,
            int errorCount) {
        return new DocumentValidated(documentSetId, documentId, versionId, false, errorCount, Instant.now());
    }
}
