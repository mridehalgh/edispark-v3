package com.edispark.mft.infrastructure.as2;

public final class As2AdmissionRejectedException extends RuntimeException {
    public As2AdmissionRejectedException() {
        super("AS2 service is temporarily at capacity");
    }
}
