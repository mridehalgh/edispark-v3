package com.tradacoms.parser.validation;

/**
 * Severity levels for validation issues.
 */
public enum Severity {
    /**
     * Critical error that prevents processing.
     */
    ERROR,
    
    /**
     * Warning that may indicate a problem but doesn't prevent processing.
     */
    WARNING,
    
    /**
     * Informational message for awareness.
     */
    INFO
}
