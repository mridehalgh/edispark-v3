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
        UUID partnerSigningCertificateId,
        UUID partnerEncryptionCertificateId,
        UUID previousPartnerSigningCertificateId,
        Instant signingCertificateGraceUntil,
        URI asynchronousMdnEndpoint,
        MdnMode mdnMode,
        boolean signingRequired,
        boolean encryptionRequired,
        boolean compressionEnabled,
        boolean legacyCryptoEnabled,
        PartnerStatus status,
        Instant createdAt) {

    public PartnerProfile {
        Objects.requireNonNull(id, "id must not be null");
        requireText(tenantId, "tenantId");
        requireText(name, "name");
        requireAs2Id(localAs2Id, "localAs2Id");
        requireAs2Id(remoteAs2Id, "remoteAs2Id");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(localCertificateId, "localCertificateId must not be null");
        Objects.requireNonNull(partnerSigningCertificateId, "partnerSigningCertificateId must not be null");
        Objects.requireNonNull(partnerEncryptionCertificateId, "partnerEncryptionCertificateId must not be null");
        Objects.requireNonNull(mdnMode, "mdnMode must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if ((previousPartnerSigningCertificateId == null) != (signingCertificateGraceUntil == null)) {
            throw new IllegalArgumentException(
                    "previousPartnerSigningCertificateId and signingCertificateGraceUntil must be set together");
        }
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("endpoint must use HTTP or HTTPS");
        }
        if (asynchronousMdnEndpoint != null
                && !"http".equalsIgnoreCase(asynchronousMdnEndpoint.getScheme())
                && !"https".equalsIgnoreCase(asynchronousMdnEndpoint.getScheme())) {
            throw new IllegalArgumentException("asynchronousMdnEndpoint must use HTTP or HTTPS");
        }
    }

    /** Compatibility constructor for existing local fixtures. New code must select both partner certificate purposes. */
    public PartnerProfile(UUID id, String tenantId, String name, String localAs2Id, String remoteAs2Id,
                          URI endpoint, UUID localCertificateId, UUID partnerCertificateId, MdnMode mdnMode,
                          boolean signingRequired, boolean encryptionRequired, boolean compressionEnabled,
                          boolean legacyCryptoEnabled, Instant createdAt) {
        this(id, tenantId, name, localAs2Id, remoteAs2Id, endpoint, localCertificateId,
                partnerCertificateId, partnerCertificateId, null, null, null, mdnMode, signingRequired,
                encryptionRequired, compressionEnabled, legacyCryptoEnabled, PartnerStatus.ACTIVE, createdAt);
    }

    public UUID partnerCertificateId() {
        return partnerEncryptionCertificateId;
    }

    public PartnerProfile activate() {
        return withStatus(PartnerStatus.ACTIVE);
    }

    public PartnerProfile suspend() {
        return withStatus(PartnerStatus.SUSPENDED);
    }

    public PartnerProfile withPartnerCertificates(UUID signingCertificateId, UUID encryptionCertificateId) {
        return withPartnerCertificates(signingCertificateId, encryptionCertificateId, null);
    }

    public PartnerProfile withPartnerCertificates(UUID signingCertificateId, UUID encryptionCertificateId,
                                                   Instant graceUntil) {
        return new PartnerProfile(id, tenantId, name, localAs2Id, remoteAs2Id, endpoint, localCertificateId,
                signingCertificateId, encryptionCertificateId,
                signingCertificateId.equals(partnerSigningCertificateId) ? previousPartnerSigningCertificateId
                        : partnerSigningCertificateId,
                signingCertificateId.equals(partnerSigningCertificateId) ? signingCertificateGraceUntil : graceUntil,
                asynchronousMdnEndpoint, mdnMode,
                signingRequired, encryptionRequired, compressionEnabled, legacyCryptoEnabled, status, createdAt);
    }

    private PartnerProfile withStatus(PartnerStatus nextStatus) {
        return new PartnerProfile(id, tenantId, name, localAs2Id, remoteAs2Id, endpoint, localCertificateId,
                partnerSigningCertificateId, partnerEncryptionCertificateId, previousPartnerSigningCertificateId,
                signingCertificateGraceUntil, asynchronousMdnEndpoint, mdnMode,
                signingRequired, encryptionRequired, compressionEnabled, legacyCryptoEnabled, nextStatus, createdAt);
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
