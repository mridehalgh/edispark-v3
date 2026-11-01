package com.edispark.partnerships.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TradingPartner(UUID id, String tenantId, String displayName, List<BusinessIdentity> identities,
                             PartnerStatus status, Instant createdAt, Instant updatedAt) {
    public TradingPartner {
        Objects.requireNonNull(id);
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (displayName == null || displayName.isBlank() || displayName.length() > 120) {
            throw new IllegalArgumentException("displayName must contain 1-120 characters");
        }
        identities = List.copyOf(identities == null ? List.of() : identities);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static TradingPartner create(String tenantId, String displayName, List<BusinessIdentity> identities, Instant now) {
        return new TradingPartner(UUID.randomUUID(), tenantId, displayName.trim(), identities, PartnerStatus.ACTIVE, now, now);
    }

    public TradingPartner suspend(Instant now) {
        return new TradingPartner(id, tenantId, displayName, identities, PartnerStatus.SUSPENDED, createdAt, now);
    }
}
