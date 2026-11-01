package com.edispark.mft.application;

public final class As2ReplayConflictException extends IllegalArgumentException {
    public As2ReplayConflictException() {
        super("AS2 Message-ID was already used with different content");
    }
}
