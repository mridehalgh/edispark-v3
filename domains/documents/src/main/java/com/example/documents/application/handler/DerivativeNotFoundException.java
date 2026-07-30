package com.example.documents.application.handler;

import com.example.documents.domain.model.DerivativeId;
import com.example.documents.domain.model.DocumentId;

/**
 * Exception thrown when a derivative cannot be found within a document.
 */
public class DerivativeNotFoundException extends RuntimeException {

    private final DocumentId documentId;
    private final DerivativeId derivativeId;

    public DerivativeNotFoundException(DocumentId documentId, DerivativeId derivativeId) {
        super("Derivative " + derivativeId + " not found in document " + documentId);
        this.documentId = documentId;
        this.derivativeId = derivativeId;
    }

    public DocumentId documentId() {
        return documentId;
    }

    public DerivativeId derivativeId() {
        return derivativeId;
    }
}
