package com.example.documents.application.command;

import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;

import java.util.Objects;

/**
 * Command to validate a document version against its schema.
 *
 * @param documentSetId the identifier of the document set
 * @param documentId the identifier of the document
 * @param versionNumber the version number to validate
 */
public record ValidateDocumentCommand(
        DocumentSetId documentSetId,
        DocumentId documentId,
        int versionNumber
) {
    public ValidateDocumentCommand {
        Objects.requireNonNull(documentSetId, "Document set ID cannot be null");
        Objects.requireNonNull(documentId, "Document ID cannot be null");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("Version number must be at least 1");
        }
    }

    /**
     * Creates a new ValidateDocumentCommand.
     */
    public static ValidateDocumentCommand of(
            DocumentSetId documentSetId,
            DocumentId documentId,
            int versionNumber) {
        return new ValidateDocumentCommand(documentSetId, documentId, versionNumber);
    }
}
