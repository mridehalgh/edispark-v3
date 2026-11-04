package com.edispark.partnerships.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DocumentProfile(
        UUID id,
        UUID familyId,
        String tenantId,
        UUID partnerId,
        int revision,
        String name,
        ExchangeDirection direction,
        BusinessDocumentType documentType,
        DocumentProfileStatus status,
        List<DocumentFieldRule> fieldRules,
        Instant createdAt,
        Instant updatedAt) {
    public DocumentProfile {
        Objects.requireNonNull(id);
        familyId = familyId == null ? id : familyId;
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        Objects.requireNonNull(partnerId);
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        if (name == null || name.isBlank() || name.length() > 120) throw new IllegalArgumentException("name is invalid");
        Objects.requireNonNull(direction);
        Objects.requireNonNull(documentType);
        Objects.requireNonNull(status);
        fieldRules = List.copyOf(fieldRules == null ? List.of() : fieldRules);
        if (new HashSet<>(fieldRules.stream().map(DocumentFieldRule::fieldKey).toList()).size() != fieldRules.size()) {
            throw new IllegalArgumentException("field rule keys must be unique");
        }
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static DocumentProfile draft(String tenantId, UUID partnerId, String name,
            ExchangeDirection direction, BusinessDocumentType documentType, List<DocumentFieldRule> rules,
            Instant now) {
        UUID id = UUID.randomUUID();
        return new DocumentProfile(id, id, tenantId, partnerId, 1, name, direction, documentType,
                DocumentProfileStatus.DRAFT, rules, now, now);
    }

    public DocumentProfile revise(List<DocumentFieldRule> rules, Instant now) {
        if (status != DocumentProfileStatus.DRAFT) throw new IllegalStateException("Only draft profiles can be edited");
        return new DocumentProfile(id, familyId, tenantId, partnerId, revision, name, direction, documentType,
                status, rules, createdAt, now);
    }

    public DocumentProfile publish(Instant now) {
        if (status != DocumentProfileStatus.DRAFT) throw new IllegalStateException("Only draft profiles can be published");
        return new DocumentProfile(id, familyId, tenantId, partnerId, revision, name, direction, documentType,
                DocumentProfileStatus.PUBLISHED, fieldRules, createdAt, now);
    }

    public DocumentProfile newRevision(Instant now) {
        if (status != DocumentProfileStatus.PUBLISHED) throw new IllegalStateException("Only published profiles can be revised");
        return new DocumentProfile(UUID.randomUUID(), familyId, tenantId, partnerId, revision + 1, name,
                direction, documentType, DocumentProfileStatus.DRAFT, fieldRules, now, now);
    }

    public DocumentProfile archive(Instant now) {
        if (status == DocumentProfileStatus.DRAFT) throw new IllegalStateException("Discard draft profiles instead of archiving them");
        return new DocumentProfile(id, familyId, tenantId, partnerId, revision, name, direction, documentType,
                DocumentProfileStatus.ARCHIVED, fieldRules, createdAt, now);
    }
}
