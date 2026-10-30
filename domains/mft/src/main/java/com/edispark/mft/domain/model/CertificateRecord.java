package com.edispark.mft.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CertificateRecord(
        UUID id,
        String tenantId,
        String name,
        CertificateKind kind,
        String fingerprintSha256,
        String subject,
        String issuer,
        Instant notBefore,
        Instant notAfter,
        boolean hasPrivateKey,
        String secretRef,
        Instant createdAt) {
    public CertificateRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(notBefore);
        Objects.requireNonNull(notAfter);
        Objects.requireNonNull(createdAt);
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (fingerprintSha256 == null || fingerprintSha256.isBlank()) throw new IllegalArgumentException("fingerprint must not be blank");
        if (secretRef == null || secretRef.isBlank()) throw new IllegalArgumentException("secretRef must not be blank");
    }
}
