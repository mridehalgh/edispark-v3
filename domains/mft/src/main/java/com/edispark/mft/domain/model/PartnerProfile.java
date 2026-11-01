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
        Instant createdAt,
        UUID familyId,
        int revision,
        Instant scheduledAt,
        Instant archivedAt,
        Instant updatedAt,
        long version) {

    public PartnerProfile {
        Objects.requireNonNull(id, "id must not be null");
        familyId = familyId == null ? id : familyId;
        if (revision < 1) revision = 1;
        if (version < 1) version = 1;
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
        updatedAt = updatedAt == null ? createdAt : updatedAt;
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

    public PartnerProfile(UUID id, String tenantId, String name, String localAs2Id, String remoteAs2Id,
            URI endpoint, UUID localCertificateId, UUID partnerSigningCertificateId,
            UUID partnerEncryptionCertificateId, UUID previousPartnerSigningCertificateId,
            Instant signingCertificateGraceUntil, URI asynchronousMdnEndpoint, MdnMode mdnMode,
            boolean signingRequired, boolean encryptionRequired, boolean compressionEnabled,
            boolean legacyCryptoEnabled, PartnerStatus status, Instant createdAt) {
        this(id, tenantId, name, localAs2Id, remoteAs2Id, endpoint, localCertificateId,
                partnerSigningCertificateId, partnerEncryptionCertificateId, previousPartnerSigningCertificateId,
                signingCertificateGraceUntil, asynchronousMdnEndpoint, mdnMode, signingRequired,
                encryptionRequired, compressionEnabled, legacyCryptoEnabled, status, createdAt,
                id, 1, null, null, createdAt, 1);
    }

    /** Compatibility constructor for existing local fixtures. New code must select both partner certificate purposes. */
    public PartnerProfile(UUID id, String tenantId, String name, String localAs2Id, String remoteAs2Id,
                          URI endpoint, UUID localCertificateId, UUID partnerCertificateId, MdnMode mdnMode,
                          boolean signingRequired, boolean encryptionRequired, boolean compressionEnabled,
                          boolean legacyCryptoEnabled, Instant createdAt) {
        this(id, tenantId, name, localAs2Id, remoteAs2Id, endpoint, localCertificateId,
                partnerCertificateId, partnerCertificateId, null, null, null, mdnMode, signingRequired,
                encryptionRequired, compressionEnabled, legacyCryptoEnabled, PartnerStatus.ACTIVE, createdAt,
                id, 1, null, null, createdAt, 1);
    }

    public UUID partnerCertificateId() {
        return partnerEncryptionCertificateId;
    }

    public PartnerProfile activate() {
        return withStatus(PartnerStatus.ACTIVE, null, Instant.now());
    }

    public PartnerProfile suspend() {
        return withStatus(PartnerStatus.SUSPENDED, null, Instant.now());
    }

    public PartnerProfile activate(Instant now) { return withStatus(PartnerStatus.ACTIVE, null, now); }
    public PartnerProfile schedule(Instant effectiveAt, Instant now) {
        if (status != PartnerStatus.DRAFT) throw new IllegalStateException("Only draft AS2 revisions can be scheduled");
        return withStatus(PartnerStatus.SCHEDULED, effectiveAt, now);
    }
    public PartnerProfile activateDue(Instant now) {
        return status == PartnerStatus.SCHEDULED && !scheduledAt.isAfter(now) ? activate(now) : this;
    }
    public PartnerProfile supersede(Instant now) {
        return status == PartnerStatus.ACTIVE ? withStatus(PartnerStatus.SUPERSEDED, null, now) : this;
    }
    public PartnerProfile archive(Instant now) {
        if (status == PartnerStatus.ACTIVE || status == PartnerStatus.SCHEDULED) {
            throw new IllegalStateException("Active or scheduled AS2 revisions must be suspended or cancelled first");
        }
        return new PartnerProfile(id, tenantId, name, localAs2Id, remoteAs2Id, endpoint, localCertificateId,
                partnerSigningCertificateId, partnerEncryptionCertificateId, previousPartnerSigningCertificateId,
                signingCertificateGraceUntil, asynchronousMdnEndpoint, mdnMode, signingRequired, encryptionRequired,
                compressionEnabled, legacyCryptoEnabled, PartnerStatus.ARCHIVED, createdAt, familyId, revision,
                null, now, now, version + 1);
    }

    public PartnerProfile newDraft(Instant now) {
        if (status != PartnerStatus.ACTIVE && status != PartnerStatus.SUSPENDED) {
            throw new IllegalStateException("Only active or suspended AS2 revisions can be revised");
        }
        return new PartnerProfile(UUID.randomUUID(), tenantId, name, localAs2Id, remoteAs2Id, endpoint,
                localCertificateId, partnerSigningCertificateId, partnerEncryptionCertificateId,
                previousPartnerSigningCertificateId, signingCertificateGraceUntil, asynchronousMdnEndpoint,
                mdnMode, signingRequired, encryptionRequired, compressionEnabled, legacyCryptoEnabled,
                PartnerStatus.DRAFT, now, familyId, revision + 1, null, null, now, 1);
    }

    public PartnerProfile revise(String nextName, URI nextEndpoint, URI nextAsyncMdnEndpoint, MdnMode nextMdnMode,
            boolean nextSigningRequired, boolean nextEncryptionRequired, boolean nextCompressionEnabled,
            boolean nextLegacyCryptoEnabled, Instant now) {
        if (status != PartnerStatus.DRAFT) throw new IllegalStateException("Only draft AS2 revisions are editable");
        return new PartnerProfile(id, tenantId, nextName, localAs2Id, remoteAs2Id, nextEndpoint, localCertificateId,
                partnerSigningCertificateId, partnerEncryptionCertificateId, previousPartnerSigningCertificateId,
                signingCertificateGraceUntil, nextAsyncMdnEndpoint, nextMdnMode, nextSigningRequired,
                nextEncryptionRequired, nextCompressionEnabled, nextLegacyCryptoEnabled, status, createdAt,
                familyId, revision, null, archivedAt, now, version + 1);
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
                signingRequired, encryptionRequired, compressionEnabled, legacyCryptoEnabled, status, createdAt,
                familyId, revision, scheduledAt, archivedAt, Instant.now(), version + 1);
    }

    private PartnerProfile withStatus(PartnerStatus nextStatus, Instant nextScheduledAt, Instant now) {
        return new PartnerProfile(id, tenantId, name, localAs2Id, remoteAs2Id, endpoint, localCertificateId,
                partnerSigningCertificateId, partnerEncryptionCertificateId, previousPartnerSigningCertificateId,
                signingCertificateGraceUntil, asynchronousMdnEndpoint, mdnMode,
                signingRequired, encryptionRequired, compressionEnabled, legacyCryptoEnabled, nextStatus, createdAt,
                familyId, revision, nextScheduledAt, archivedAt, now, version + 1);
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
