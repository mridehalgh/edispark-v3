package com.edispark.mft.application;

public final class As2TransportException extends RuntimeException {
    private final boolean retryable;

    public As2TransportException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
