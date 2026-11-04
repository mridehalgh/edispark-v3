package com.edispark.documents.application.handler;

import com.edispark.documents.domain.model.ValidationResult;

public final class DocumentFinalizationException extends RuntimeException {
    private final ValidationResult validationResult;

    public DocumentFinalizationException(ValidationResult validationResult) {
        super("Document must pass OASIS UBL schema validation before finalization");
        this.validationResult = validationResult;
    }

    public ValidationResult validationResult() {
        return validationResult;
    }
}
