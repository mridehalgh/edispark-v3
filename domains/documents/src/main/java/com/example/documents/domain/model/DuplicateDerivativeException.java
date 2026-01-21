package com.example.documents.domain.model;

/**
 * Exception thrown when attempting to create a derivative that already exists.
 * 
 * <p>A duplicate derivative is one with the same source version ID and target format
 * as an existing derivative.</p>
 */
public class DuplicateDerivativeException extends RuntimeException {

    private final DocumentVersionId sourceVersionId;
    private final Format targetFormat;

    public DuplicateDerivativeException(DocumentVersionId sourceVersionId, Format targetFormat) {
        super("Derivative already exists for source version " + sourceVersionId + " and format " + targetFormat);
        this.sourceVersionId = sourceVersionId;
        this.targetFormat = targetFormat;
    }

    public DocumentVersionId sourceVersionId() {
        return sourceVersionId;
    }

    public Format targetFormat() {
        return targetFormat;
    }
}
