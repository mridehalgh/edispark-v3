package com.tradacoms.parser.validation;

/**
 * Overall validation status for a batch or message.
 */
public enum ValidationStatus {
    /**
     * All validations passed with no issues.
     */
    PASS,
    
    /**
     * Validation completed with warnings but no errors.
     */
    WARN,
    
    /**
     * Validation failed with one or more errors.
     */
    FAIL;

    /**
     * Derives the overall status from the presence of errors and warnings.
     */
    public static ValidationStatus derive(boolean hasErrors, boolean hasWarnings) {
        if (hasErrors) {
            return FAIL;
        }
        if (hasWarnings) {
            return WARN;
        }
        return PASS;
    }

    /**
     * Combines two statuses, returning the more severe one.
     */
    public ValidationStatus combine(ValidationStatus other) {
        if (this == FAIL || other == FAIL) {
            return FAIL;
        }
        if (this == WARN || other == WARN) {
            return WARN;
        }
        return PASS;
    }
}
