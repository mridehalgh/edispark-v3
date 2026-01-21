package com.example.documents.domain.event;

import com.example.documents.domain.model.DocumentSetId;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a new DocumentSet is created.
 * 
 * <p>Requirements: 9.1, 9.5, 9.6</p>
 *
 * @param documentSetId the identifier of the created document set
 * @param createdBy the user who created the document set
 * @param occurredAt the timestamp when the event occurred
 */
public record DocumentSetCreated(
        DocumentSetId documentSetId,
        String createdBy,
        Instant occurredAt
) implements DomainEvent {

    public DocumentSetCreated {
        Objects.requireNonNull(documentSetId, "DocumentSetId cannot be null");
        Objects.requireNonNull(createdBy, "CreatedBy cannot be null");
        Objects.requireNonNull(occurredAt, "OccurredAt cannot be null");
    }

    /**
     * Creates a new DocumentSetCreated event with the current timestamp.
     */
    public static DocumentSetCreated now(DocumentSetId documentSetId, String createdBy) {
        return new DocumentSetCreated(documentSetId, createdBy, Instant.now());
    }
}
