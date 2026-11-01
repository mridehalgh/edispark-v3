package com.edispark.partnerships.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TradingAgreement(UUID id, String tenantId, UUID partnerId, int revision, String name,
        AgreementStatus status, Instant effectiveFrom, Instant effectiveUntil, List<ExchangeCapability> capabilities,
        QualificationReport qualification, Instant createdAt, Instant updatedAt, UUID familyId,
        Instant scheduledAt, Instant archivedAt, long version) {
    public TradingAgreement {
        Objects.requireNonNull(id);
        familyId = familyId == null ? id : familyId;
        if (version < 1) version = 1;
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

    public TradingAgreement(UUID id, String tenantId, UUID partnerId, int revision, String name,
            AgreementStatus status, Instant effectiveFrom, Instant effectiveUntil, List<ExchangeCapability> capabilities,
            QualificationReport qualification, Instant createdAt, Instant updatedAt) {
        this(id, tenantId, partnerId, revision, name, status, effectiveFrom, effectiveUntil, capabilities,
                qualification, createdAt, updatedAt, id, null, null, 1);
    }

    public static TradingAgreement draft(String tenantId, UUID partnerId, String name, Instant now) {
        UUID id = UUID.randomUUID();
        return new TradingAgreement(id, tenantId, partnerId, 1, name, AgreementStatus.DRAFT,
                null, null, List.of(), null, now, now, id, null, null, 1);
    }

    public TradingAgreement addCapability(ExchangeCapability capability, Instant now) {
        requireEditable();
        if (capabilities.stream().anyMatch(item -> item.routingKey().equals(capability.routingKey()))) {
            throw new IllegalArgumentException("Agreement already has capability " + capability.routingKey());
        }
        var next = new java.util.ArrayList<>(capabilities);
        next.add(capability);
        return new TradingAgreement(id, tenantId, partnerId, revision, name, status, effectiveFrom, effectiveUntil,
                next, qualification, createdAt, now, familyId, scheduledAt, archivedAt, version + 1);
    }

    public TradingAgreement replaceCapability(UUID capabilityId, ExchangeCapability capability, Instant now) {
        requireEditable();
        if (capabilities.stream().noneMatch(item -> item.id().equals(capabilityId))) {
            throw new IllegalArgumentException("Agreement capability was not found");
        }
        List<ExchangeCapability> next = capabilities.stream()
                .map(item -> item.id().equals(capabilityId) ? capability : item).toList();
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.DRAFT,
                effectiveFrom, effectiveUntil, next, null, createdAt, now, familyId, null, archivedAt, version + 1);
    }

    public TradingAgreement removeCapability(UUID capabilityId, Instant now) {
        requireEditable();
        List<ExchangeCapability> next = capabilities.stream().filter(item -> !item.id().equals(capabilityId)).toList();
        if (next.size() == capabilities.size()) throw new IllegalArgumentException("Agreement capability was not found");
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.DRAFT,
                effectiveFrom, effectiveUntil, next, null, createdAt, now, familyId, null, archivedAt, version + 1);
    }

    public TradingAgreement qualify(QualificationReport report, Instant now) {
        requireEditable();
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.QUALIFYING,
                effectiveFrom, effectiveUntil, capabilities, Objects.requireNonNull(report), createdAt, now,
                familyId, null, archivedAt, version + 1);
    }

    public TradingAgreement activate(Instant now) {
        if (qualification == null || !qualification.passed()) throw new IllegalStateException("Qualification must pass before activation");
        if (capabilities.isEmpty()) throw new IllegalStateException("At least one capability is required");
        if (new HashSet<>(capabilities.stream().map(ExchangeCapability::routingKey).toList()).size() != capabilities.size()) {
            throw new IllegalStateException("Capability routing keys must be unique");
        }
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.ACTIVE,
                effectiveFrom == null ? now : effectiveFrom, effectiveUntil, capabilities, qualification, createdAt, now,
                familyId, null, archivedAt, version + 1);
    }

    public TradingAgreement schedule(Instant effectiveAt, Instant now) {
        if (qualification == null || !qualification.passed()) throw new IllegalStateException("Qualification must pass before scheduling");
        if (!effectiveAt.isAfter(now)) return activate(now);
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.SCHEDULED,
                effectiveAt, effectiveUntil, capabilities, qualification, createdAt, now,
                familyId, effectiveAt, archivedAt, version + 1);
    }

    public TradingAgreement activateDue(Instant now) {
        if (status != AgreementStatus.SCHEDULED || scheduledAt.isAfter(now)) return this;
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.ACTIVE,
                scheduledAt, effectiveUntil, capabilities, qualification, createdAt, now,
                familyId, null, archivedAt, version + 1);
    }

    public TradingAgreement suspend(Instant now) {
        if (status != AgreementStatus.ACTIVE) throw new IllegalStateException("Only active agreements can be suspended");
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.SUSPENDED,
                effectiveFrom, effectiveUntil, capabilities, qualification, createdAt, now,
                familyId, null, archivedAt, version + 1);
    }

    public TradingAgreement newRevision(Instant now) {
        if (status != AgreementStatus.ACTIVE && status != AgreementStatus.SUSPENDED) {
            throw new IllegalStateException("Only active or suspended agreements can be revised");
        }
        return new TradingAgreement(UUID.randomUUID(), tenantId, partnerId, revision + 1, name,
                AgreementStatus.DRAFT, null, null, capabilities, null, now, now,
                familyId, null, null, 1);
    }

    public TradingAgreement supersede(Instant now) {
        if (status != AgreementStatus.ACTIVE) return this;
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.SUPERSEDED,
                effectiveFrom, effectiveUntil, capabilities, qualification, createdAt, now,
                familyId, null, archivedAt, version + 1);
    }

    public TradingAgreement archive(Instant now) {
        if (status == AgreementStatus.ACTIVE || status == AgreementStatus.SCHEDULED) {
            throw new IllegalStateException("Active or scheduled agreements must be suspended or cancelled first");
        }
        return new TradingAgreement(id, tenantId, partnerId, revision, name, AgreementStatus.ARCHIVED,
                effectiveFrom, effectiveUntil, capabilities, qualification, createdAt, now,
                familyId, null, now, version + 1);
    }

    private void requireEditable() {
        if (status != AgreementStatus.DRAFT && status != AgreementStatus.QUALIFYING) {
            throw new IllegalStateException("Active agreement revisions are immutable");
        }
    }
}
