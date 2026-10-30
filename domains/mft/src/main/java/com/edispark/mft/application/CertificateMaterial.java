package com.edispark.mft.application;

public record CertificateMaterial(byte[] bytes, String password, String alias) {
    public CertificateMaterial {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
