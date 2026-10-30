package com.edispark.mft.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PartnerProfile(
        UUID id,
        String tenantId,
        String name,
        String localAs2Id,
        String remoteAs2Id,
        URI endpoint,
        UUID localCertificateId,
        UUID partnerCertificateId,
        MdnMode mdnMode,
        boolean signingRequired,
        boolean encryptionRequired,
        boolean compressionEnabled,
        boolean legacyCryptoEnabled,
        Instant createdAt) {

    public PartnerProfile {
        Objects.requireNonNull(id, "id must not be null");
        requireText(tenantId, "tenantId");
        requireText(name, "name");
        requireAs2Id(localAs2Id, "localAs2Id");
        requireAs2Id(remoteAs2Id, "remoteAs2Id");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(localCertificateId, "localCertificateId must not be null");
        Objects.requireNonNull(partnerCertificateId, "partnerCertificateId must not be null");
        Objects.requireNonNull(mdnMode, "mdnMode must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("endpoint must use HTTP or HTTPS");
        }
    }

    private static void requireAs2Id(String value, String field) {
        requireText(value, field);
        if (value.length() > 128 || value.chars().anyMatch(character -> character < 32 || character > 126)) {
            throw new IllegalArgumentException(field + " must contain 1-128 printable ASCII characters");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
