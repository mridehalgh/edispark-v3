package com.example.documents.application.handler;

import com.example.documents.domain.model.DocumentSetId;

/**
 * Exception thrown when a DocumentSet cannot be found.
 */
public class DocumentSetNotFoundException extends RuntimeException {

    private final DocumentSetId documentSetId;

    public DocumentSetNotFoundException(DocumentSetId documentSetId) {
        super("DocumentSet not found: " + documentSetId);
        this.documentSetId = documentSetId;
    }

    public DocumentSetId documentSetId() {
        return documentSetId;
    }
}
