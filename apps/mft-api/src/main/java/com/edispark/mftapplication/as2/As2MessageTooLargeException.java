package com.edispark.mftapplication.as2;

final class As2MessageTooLargeException extends RuntimeException {
    As2MessageTooLargeException() {
        super("AS2 message exceeds the configured size limit");
    }
}
