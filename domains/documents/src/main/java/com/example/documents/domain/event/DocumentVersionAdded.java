package com.example.documents.domain.event;

import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentVersionId;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a new version is added to a document.
 * 
 * <p>Requirements: 9.2, 9.5, 9.6</p>
 *
 * @param documentSetId the identifier of the document set
 * @param documentId the identifier of the document
 * @param versionId the identifier of the new version
 * @param versionNumber the sequential version number
 * @param contentHash the hash of the version content
 * @param occurredAt the timestamp when the event occurred
 */
public record DocumentVersionAdded(
        DocumentSetId documentSetId,
        DocumentId documentId,
        DocumentVersionId versionId,
        int versionNumber,
        ContentHash contentHash,
        Instant occurredAt
) implements DomainEvent {

    public DocumentVersionAdded {
        Objects.requireNonNull(documentSetId, "DocumentSetId cannot be null");
        Objects.requireNonNull(documentId, "DocumentId cannot be null");
        Objects.requireNonNull(versionId, "VersionId cannot be null");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("Version number must be at least 1");
        }
        Objects.requireNonNull(contentHash, "ContentHash cannot be null");
        Objects.requireNonNull(occurredAt, "OccurredAt cannot be null");
    }

    /**
     * Creates a new DocumentVersionAdded event with the current timestamp.
     */
    public static DocumentVersionAdded now(
            DocumentSetId documentSetId,
            DocumentId documentId,
            DocumentVersionId versionId,
            int versionNumber,
            ContentHash contentHash) {
        return new DocumentVersionAdded(
                documentSetId, documentId, versionId, versionNumber, contentHash, Instant.now());
    }
}
