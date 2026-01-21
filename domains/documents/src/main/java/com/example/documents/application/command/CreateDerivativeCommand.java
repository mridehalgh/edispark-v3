package com.example.documents.application.command;

import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.Format;

import java.util.Objects;

/**
 * Command to create a derivative from a document version.
 *
 * @param documentSetId the identifier of the document set
 * @param documentId the identifier of the document
 * @param sourceVersionNumber the version number to create the derivative from
 * @param targetFormat the desired output format for the derivative
 */
public record CreateDerivativeCommand(
        DocumentSetId documentSetId,
        DocumentId documentId,
        int sourceVersionNumber,
        Format targetFormat
) {
    public CreateDerivativeCommand {
        Objects.requireNonNull(documentSetId, "Document set ID cannot be null");
        Objects.requireNonNull(documentId, "Document ID cannot be null");
        if (sourceVersionNumber < 1) {
            throw new IllegalArgumentException("Source version number must be at least 1");
        }
        Objects.requireNonNull(targetFormat, "Target format cannot be null");
    }

    /**
     * Creates a new CreateDerivativeCommand.
     */
    public static CreateDerivativeCommand of(
            DocumentSetId documentSetId,
            DocumentId documentId,
            int sourceVersionNumber,
            Format targetFormat) {
        return new CreateDerivativeCommand(documentSetId, documentId, sourceVersionNumber, targetFormat);
    }
}
