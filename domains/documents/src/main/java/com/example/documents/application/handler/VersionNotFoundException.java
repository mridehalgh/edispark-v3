package com.example.documents.application.handler;

import com.example.documents.domain.model.DocumentId;

/**
 * Exception thrown when a DocumentVersion cannot be found.
 */
public class VersionNotFoundException extends RuntimeException {

    private final DocumentId documentId;
    private final int versionNumber;

    public VersionNotFoundException(DocumentId documentId, int versionNumber) {
        super("Version " + versionNumber + " not found for document " + documentId);
        this.documentId = documentId;
        this.versionNumber = versionNumber;
    }

    public DocumentId documentId() {
        return documentId;
    }

    public int versionNumber() {
        return versionNumber;
    }
}
