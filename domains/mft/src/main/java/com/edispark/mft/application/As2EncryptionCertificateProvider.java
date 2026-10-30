package com.edispark.mft.application;

import com.edispark.mft.domain.model.CertificateRecord;
import java.time.Instant;

@FunctionalInterface
public interface As2EncryptionCertificateProvider {
    PublicCertificate encryptionCertificate(CertificateRecord signingCertificate);

    record PublicCertificate(String name, String fingerprintSha256, Instant notAfter, String pem) {}
}
