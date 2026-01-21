package com.example.documents.domain.event;

import com.example.documents.domain.model.DerivativeId;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentVersionId;
import com.example.documents.domain.model.Format;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a derivative is created from a document version.
 * 
 * <p>Requirements: 9.3, 9.5, 9.6</p>
 *
 * @param documentSetId the identifier of the document set
 * @param documentId the identifier of the document
 * @param derivativeId the identifier of the created derivative
 * @param sourceVersionId the identifier of the source version
 * @param targetFormat the format of the derivative
 * @param occurredAt the timestamp when the event occurred
 */
public record DerivativeCreated(
        DocumentSetId documentSetId,
        DocumentId documentId,
        DerivativeId derivativeId,
        DocumentVersionId sourceVersionId,
        Format targetFormat,
        Instant occurredAt
) implements DomainEvent {

    public DerivativeCreated {
        Objects.requireNonNull(documentSetId, "DocumentSetId cannot be null");
        Objects.requireNonNull(documentId, "DocumentId cannot be null");
        Objects.requireNonNull(derivativeId, "DerivativeId cannot be null");
        Objects.requireNonNull(sourceVersionId, "SourceVersionId cannot be null");
        Objects.requireNonNull(targetFormat, "TargetFormat cannot be null");
        Objects.requireNonNull(occurredAt, "OccurredAt cannot be null");
    }

    /**
     * Creates a new DerivativeCreated event with the current timestamp.
     */
    public static DerivativeCreated now(
            DocumentSetId documentSetId,
            DocumentId documentId,
            DerivativeId derivativeId,
            DocumentVersionId sourceVersionId,
            Format targetFormat) {
        return new DerivativeCreated(
                documentSetId, documentId, derivativeId, sourceVersionId, targetFormat, Instant.now());
    }
}
