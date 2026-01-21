package com.example.documents.application.handler;

import com.example.documents.domain.model.ValidationResult;

/**
 * Exception thrown when document validation fails.
 */
public class ValidationException extends RuntimeException {

    private final ValidationResult validationResult;

    public ValidationException(ValidationResult validationResult) {
        super("Document validation failed with " + validationResult.errorCount() + " error(s)");
        this.validationResult = validationResult;
    }

    public ValidationResult validationResult() {
        return validationResult;
    }
}
