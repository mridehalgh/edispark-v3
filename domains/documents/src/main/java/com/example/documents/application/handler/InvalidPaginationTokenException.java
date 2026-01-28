package com.example.documents.application.handler;

/**
 * Thrown when a pagination token cannot be decoded or is invalid.
 */
public class InvalidPaginationTokenException extends RuntimeException {
    public InvalidPaginationTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
