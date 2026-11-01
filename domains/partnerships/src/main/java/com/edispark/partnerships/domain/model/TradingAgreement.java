package com.edispark.partnerships.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TradingAgreement(UUID id, String tenantId, UUID partnerId, int revision, String name,
        AgreementStatus status, Instant effectiveFrom, Instant effectiveUntil, List<ExchangeCapability> capabilities,
        QualificationReport qualification, Instant createdAt, Instant updatedAt) {
    public TradingAgreement {
        Objects.requireNonNull(id);
        Objects.requireNonNull(partnerId);
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        if (name == null || name.isBlank() || name.length() > 120) throw new IllegalArgumentException("name is invalid");
        Objects.requireNonNull(status);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (effectiveUntil != null && effectiveFrom != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
        }
    }

    public static TradingAgreement draft(String tenantId, UUID partnerId, String name, Instant now) {
        return new TradingAgreement(UUID.randomUUID(), tenantId, partnerId, 1, name, AgreementStatus.DRAFT,
                null, null, List.of(), null, now, now);
    }

    public TradingAgreement addCapability(ExchangeCapability capability, Instant now) {
        requireEditable();
        if (capabilities.stream().anyMatch(item -> item.routingKey().equals(capability.routingKey()))) {
            throw new IllegalArgumentException("Agreement already has capability " + capability.routingKey());
        }
        var next = new java.util.ArrayList<>(capabilities);
        next.add(capability);
        return new TradingAgreement(id, tenantId, partnerId, revision, name, status, effectiveFrom, effectiveUntil,
                next, qualification, createdAt, now);
    }

    public TradingAgreement qualify(QualificationReport report, Instant now) {
        requireEditable();
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.QUALIFYING,
                effectiveFrom, effectiveUntil, capabilities, Objects.requireNonNull(report), createdAt, now);
    }

    public TradingAgreement activate(Instant now) {
        if (qualification == null || !qualification.passed()) throw new IllegalStateException("Qualification must pass before activation");
        if (capabilities.isEmpty()) throw new IllegalStateException("At least one capability is required");
        if (new HashSet<>(capabilities.stream().map(ExchangeCapability::routingKey).toList()).size() != capabilities.size()) {
            throw new IllegalStateException("Capability routing keys must be unique");
        }
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.ACTIVE,
                effectiveFrom == null ? now : effectiveFrom, effectiveUntil, capabilities, qualification, createdAt, now);
    }

    public TradingAgreement suspend(Instant now) {
        if (status != AgreementStatus.ACTIVE) throw new IllegalStateException("Only active agreements can be suspended");
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.SUSPENDED,
                effectiveFrom, effectiveUntil, capabilities, qualification, createdAt, now);
    }

    public TradingAgreement newRevision(Instant now) {
        if (status != AgreementStatus.ACTIVE && status != AgreementStatus.SUSPENDED) {
            throw new IllegalStateException("Only active or suspended agreements can be revised");
        }
        return new TradingAgreement(UUID.randomUUID(), tenantId, partnerId, revision + 1, name,
                AgreementStatus.DRAFT, null, null, capabilities, null, now, now);
    }

    private void requireEditable() {
        if (status != AgreementStatus.DRAFT && status != AgreementStatus.QUALIFYING) {
            throw new IllegalStateException("Active agreement revisions are immutable");
        }
    }
}
