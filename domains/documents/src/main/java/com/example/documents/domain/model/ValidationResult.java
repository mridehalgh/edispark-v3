package com.example.documents.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of validating a document against its schema.
 *
 * @param valid whether the document passed validation
 * @param errors list of validation errors (empty if valid)
 * @param warnings list of validation warnings
 */
public record ValidationResult(boolean valid, List<ValidationError> errors, List<ValidationWarning> warnings) {

    public ValidationResult {
        Objects.requireNonNull(errors, "Errors list cannot be null");
        Objects.requireNonNull(warnings, "Warnings list cannot be null");
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    /**
     * Creates a successful validation result with no errors or warnings.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }

    /**
     * Creates a successful validation result with warnings.
     */
    public static ValidationResult successWithWarnings(List<ValidationWarning> warnings) {
        return new ValidationResult(true, List.of(), warnings);
    }

    /**
     * Creates a failed validation result with the given errors.
     */
    public static ValidationResult failure(List<ValidationError> errors) {
        return new ValidationResult(false, errors, List.of());
    }

    /**
     * Creates a failed validation result with errors and warnings.
     */
    public static ValidationResult failure(List<ValidationError> errors, List<ValidationWarning> warnings) {
        return new ValidationResult(false, errors, warnings);
    }

    /**
     * Returns the number of errors.
     */
    public int errorCount() {
        return errors.size();
    }

    /**
     * Returns the number of warnings.
     */
    public int warningCount() {
        return warnings.size();
    }

    /**
     * Returns true if there are any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
