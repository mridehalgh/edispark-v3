package com.tradacoms.parser.batch;

/**
 * Status of file processing in batch operations.
 */
public enum FileStatus {
    /**
     * File was processed successfully with all messages valid.
     */
    SUCCESS,
    
    /**
     * File was processed but some messages had validation issues.
     */
    PARTIAL,
    
    /**
     * File processing failed completely.
     */
    FAILED
}
