package com.example.documents.domain.model;

/**
 * Exception thrown when attempting to create a document version that would break the version sequence.
 * 
 * <p>Version numbers must form a contiguous sequence starting from 1, and each version
 * (except the first) must reference its predecessor.</p>
 */
public class InvalidVersionSequenceException extends RuntimeException {

    private final DocumentId documentId;
    private final int expectedVersionNumber;
    private final int actualVersionNumber;

    public InvalidVersionSequenceException(DocumentId documentId, int expectedVersionNumber, int actualVersionNumber) {
        super("Invalid version sequence for document " + documentId + 
              ": expected version " + expectedVersionNumber + 
              " but got " + actualVersionNumber);
        this.documentId = documentId;
        this.expectedVersionNumber = expectedVersionNumber;
        this.actualVersionNumber = actualVersionNumber;
    }

    public DocumentId documentId() {
        return documentId;
    }

    public int expectedVersionNumber() {
        return expectedVersionNumber;
    }

    public int actualVersionNumber() {
        return actualVersionNumber;
    }
}