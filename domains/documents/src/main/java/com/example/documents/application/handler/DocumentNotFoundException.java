package com.example.documents.application.handler;

import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;

/**
 * Exception thrown when a Document cannot be found within a DocumentSet.
 */
public class DocumentNotFoundException extends RuntimeException {

    private final DocumentSetId documentSetId;
    private final DocumentId documentId;

    public DocumentNotFoundException(DocumentSetId documentSetId, DocumentId documentId) {
        super("Document " + documentId + " not found in DocumentSet " + documentSetId);
        this.documentSetId = documentSetId;
        this.documentId = documentId;
    }

    public DocumentSetId documentSetId() {
        return documentSetId;
    }

    public DocumentId documentId() {
        return documentId;
    }
}
