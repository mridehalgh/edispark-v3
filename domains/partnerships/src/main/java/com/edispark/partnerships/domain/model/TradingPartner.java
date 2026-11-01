package com.edispark.partnerships.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TradingPartner(UUID id, String tenantId, String displayName, List<BusinessIdentity> identities,
                             PartnerStatus status, Instant createdAt, Instant updatedAt, UUID familyId,
                             int revision, Instant scheduledAt, Instant archivedAt, long version) {
    public TradingPartner {
        Objects.requireNonNull(id);
        familyId = familyId == null ? id : familyId;
        if (revision < 1) revision = 1;
        if (version < 1) version = 1;
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (displayName == null || displayName.isBlank() || displayName.length() > 120) {
            throw new IllegalArgumentException("displayName must contain 1-120 characters");
        }
        identities = List.copyOf(identities == null ? List.of() : identities);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public TradingPartner(UUID id, String tenantId, String displayName, List<BusinessIdentity> identities,
                          PartnerStatus status, Instant createdAt, Instant updatedAt) {
        this(id, tenantId, displayName, identities, status, createdAt, updatedAt, id, 1, null, null, 1);
    }

    public static TradingPartner create(String tenantId, String displayName, List<BusinessIdentity> identities, Instant now) {
        UUID id = UUID.randomUUID();
        return new TradingPartner(id, tenantId, displayName.trim(), identities, PartnerStatus.ACTIVE, now, now,
                id, 1, null, null, 1);
    }

    public TradingPartner suspend(Instant now) {
        return copy(displayName, identities, PartnerStatus.SUSPENDED, null, archivedAt, now);
    }

    public TradingPartner newDraft(Instant now) {
        if (status != PartnerStatus.ACTIVE && status != PartnerStatus.SUSPENDED) {
            throw new IllegalStateException("Only active or suspended partner revisions can be revised");
        }
        return new TradingPartner(UUID.randomUUID(), tenantId, displayName, identities, PartnerStatus.DRAFT,
                now, now, familyId, revision + 1, null, null, 1);
    }

    public TradingPartner revise(String nextName, List<BusinessIdentity> nextIdentities, Instant now) {
        if (status != PartnerStatus.DRAFT) throw new IllegalStateException("Only draft partner revisions are editable");
        return copy(nextName.trim(), nextIdentities, status, null, archivedAt, now);
    }

    public TradingPartner activate(Instant now) {
        if (status != PartnerStatus.DRAFT && status != PartnerStatus.SCHEDULED) {
            throw new IllegalStateException("Only draft or scheduled partner revisions can be activated");
        }
        return copy(displayName, identities, PartnerStatus.ACTIVE, null, archivedAt, now);
    }

    public TradingPartner schedule(Instant effectiveAt, Instant now) {
        if (status != PartnerStatus.DRAFT) throw new IllegalStateException("Only draft partner revisions can be scheduled");
        return copy(displayName, identities, PartnerStatus.SCHEDULED, effectiveAt, archivedAt, now);
    }

    public TradingPartner supersede(Instant now) {
        if (status != PartnerStatus.ACTIVE) return this;
        return copy(displayName, identities, PartnerStatus.SUPERSEDED, null, archivedAt, now);
    }

    public TradingPartner archive(Instant now) {
        if (status == PartnerStatus.ACTIVE || status == PartnerStatus.SCHEDULED) {
            throw new IllegalStateException("Active or scheduled partners must be suspended or cancelled first");
        }
        return copy(displayName, identities, PartnerStatus.ARCHIVED, null, now, now);
    }

    private TradingPartner copy(String nextName, List<BusinessIdentity> nextIdentities, PartnerStatus nextStatus,
                                Instant nextScheduledAt, Instant nextArchivedAt, Instant now) {
        return new TradingPartner(id, tenantId, nextName, nextIdentities, nextStatus, createdAt, now,
                familyId, revision, nextScheduledAt, nextArchivedAt, version + 1);
    }
}
