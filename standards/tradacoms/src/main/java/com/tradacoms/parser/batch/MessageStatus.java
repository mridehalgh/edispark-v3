package com.tradacoms.parser.batch;

/**
 * Status of individual message processing in batch operations.
 */
public enum MessageStatus {
    /**
     * Message passed validation.
     */
    VALID,
    
    /**
     * Message failed validation.
     */
    INVALID,
    
    /**
     * Message was skipped (e.g., due to filtering).
     */
    SKIPPED
}
