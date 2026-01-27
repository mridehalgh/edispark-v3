package com.example.documents.domain.model;

/**
 * Exception thrown when the computed content hash does not match the expected hash.
 * 
 * <p>This exception indicates a content integrity violation, which could be due to
 * data corruption, tampering, or incorrect hash computation.</p>
 */
public class ContentHashMismatchException extends RuntimeException {

    private final ContentHash expectedHash;
    private final ContentHash actualHash;

    public ContentHashMismatchException(ContentHash expectedHash, ContentHash actualHash) {
        super("Content hash mismatch: expected " + expectedHash + " but got " + actualHash);
        this.expectedHash = expectedHash;
        this.actualHash = actualHash;
    }

    public ContentHashMismatchException(ContentHash expectedHash, ContentHash actualHash, String message) {
        super(message + " - expected " + expectedHash + " but got " + actualHash);
        this.expectedHash = expectedHash;
        this.actualHash = actualHash;
    }

    public ContentHash expectedHash() {
        return expectedHash;
    }

    public ContentHash actualHash() {
        return actualHash;
    }
}