package com.edispark.transformations.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MappingRevision(UUID id, String tenantId, String name, int revision, UUID sourceSchemaId,
        UUID targetSchemaId, MappingStatus status, List<MappingNode> nodes, String contentHash,
        Instant createdAt, Instant publishedAt, UUID familyId, Instant scheduledAt, Instant archivedAt,
        long version, MappingScope scope, String vendor, MappingSample sample, UUID derivedFromMappingId,
        TargetValidationEvidence targetValidationEvidence) {
    private static final int MAX_NODES = 2_000;

    public MappingRevision {
        Objects.requireNonNull(id);
        familyId = familyId == null ? id : familyId;
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (name == null || name.isBlank() || name.length() > 160) throw new IllegalArgumentException("name is invalid");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        Objects.requireNonNull(sourceSchemaId);
        Objects.requireNonNull(targetSchemaId);
        Objects.requireNonNull(status);
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        if (nodes.size() > MAX_NODES) throw new IllegalArgumentException("Mapping exceeds 2000 nodes");
        Objects.requireNonNull(createdAt);
        if (version < 1) version = 1;
        scope = scope == null ? MappingScope.TENANT : scope;
        if (scope == MappingScope.GLOBAL && (vendor == null || vendor.isBlank())) {
            throw new IllegalArgumentException("Global mappings require a vendor");
        }
        if (status == MappingStatus.PUBLISHED && (contentHash == null || publishedAt == null)) {
            throw new IllegalArgumentException("Published mappings require a hash and timestamp");
        }
        if (status == MappingStatus.SCHEDULED && (contentHash == null || scheduledAt == null)) {
            throw new IllegalArgumentException("Scheduled mappings require a hash and activation time");
        }
    }

    public MappingRevision(UUID id, String tenantId, String name, int revision, UUID sourceSchemaId,
            UUID targetSchemaId, MappingStatus status, List<MappingNode> nodes, String contentHash,
            Instant createdAt, Instant publishedAt, UUID familyId, Instant scheduledAt, Instant archivedAt,
            long version, MappingScope scope, String vendor, MappingSample sample, UUID derivedFromMappingId) {
        this(id, tenantId, name, revision, sourceSchemaId, targetSchemaId, status, nodes, contentHash,
                createdAt, publishedAt, familyId, scheduledAt, archivedAt, version, scope, vendor, sample, derivedFromMappingId, null);
    }

    public MappingRevision(UUID id, String tenantId, String name, int revision, UUID sourceSchemaId,
            UUID targetSchemaId, MappingStatus status, List<MappingNode> nodes, String contentHash,
            Instant createdAt, Instant publishedAt) {
        this(id, tenantId, name, revision, sourceSchemaId, targetSchemaId, status, nodes, contentHash,
                createdAt, publishedAt, id, null, null, 1, MappingScope.TENANT, null, null, null);
    }

    public MappingRevision(UUID id, String tenantId, String name, int revision, UUID sourceSchemaId,
            UUID targetSchemaId, MappingStatus status, List<MappingNode> nodes, String contentHash,
            Instant createdAt, Instant publishedAt, UUID familyId, Instant scheduledAt, Instant archivedAt,
            long version) {
        this(id, tenantId, name, revision, sourceSchemaId, targetSchemaId, status, nodes, contentHash,
                createdAt, publishedAt, familyId, scheduledAt, archivedAt, version,
                MappingScope.TENANT, null, null, null);
    }

    public static MappingRevision draft(String tenantId, String name, UUID sourceSchemaId, UUID targetSchemaId,
                                        List<MappingNode> nodes, Instant now) {
        UUID id = UUID.randomUUID();
        return new MappingRevision(id, tenantId, name, 1, sourceSchemaId, targetSchemaId,
                MappingStatus.DRAFT, nodes, null, now, null, id, null, null, 1);
    }

    public MappingRevision publish(String hash, Instant now) {
        if (status != MappingStatus.DRAFT) throw new IllegalStateException("Only draft mappings can be published");
        if (nodes.isEmpty()) throw new IllegalStateException("A mapping must contain at least one node");
        return retained(MappingStatus.PUBLISHED, hash, now, null, null, nodes, targetValidationEvidence);
    }

    public MappingRevision schedule(String hash, Instant effectiveAt) {
        if (status != MappingStatus.DRAFT) throw new IllegalStateException("Only draft mappings can be scheduled");
        if (nodes.isEmpty()) throw new IllegalStateException("A mapping must contain at least one node");
        return retained(MappingStatus.SCHEDULED, hash, null, effectiveAt, null, nodes, targetValidationEvidence);
    }

    public MappingRevision publishDue(Instant now) {
        if (status != MappingStatus.SCHEDULED || scheduledAt.isAfter(now)) return this;
        return retained(MappingStatus.PUBLISHED, contentHash, now, null, null, nodes, targetValidationEvidence);
    }

    public MappingRevision revise(List<MappingNode> revisedNodes) {
        if (status != MappingStatus.DRAFT) throw new IllegalStateException("Published mappings are immutable");
        return retained(status, null, null, null, null, revisedNodes, null);
    }

    public MappingRevision newDraft(Instant now) {
        if (status != MappingStatus.PUBLISHED) throw new IllegalStateException("Only published mappings can be revised");
        return new MappingRevision(UUID.randomUUID(), tenantId, name, revision + 1, sourceSchemaId, targetSchemaId,
                MappingStatus.DRAFT, nodes, null, now, null, familyId, null, null, 1, scope, vendor, sample, derivedFromMappingId, null);
    }

    public MappingRevision archive(Instant now) {
        if (status == MappingStatus.DRAFT || status == MappingStatus.SCHEDULED) {
            throw new IllegalStateException("Discard the draft or cancel its schedule instead");
        }
        return retained(MappingStatus.ARCHIVED, contentHash, publishedAt, null, now, nodes, targetValidationEvidence);
    }

    public MappingRevision withTargetEvidence(TargetValidationEvidence evidence) {
        if (status != MappingStatus.DRAFT) throw new IllegalStateException("Target evidence is attached before publication");
        return retained(status, contentHash, publishedAt, scheduledAt, archivedAt, nodes, evidence);
    }

    private MappingRevision retained(MappingStatus state, String hash, Instant published, Instant scheduled,
            Instant archived, List<MappingNode> rules, TargetValidationEvidence evidence) {
        return new MappingRevision(id, tenantId, name, revision, sourceSchemaId, targetSchemaId, state,
                rules, hash, createdAt, published, familyId, scheduled, archived, version + 1,
                scope, vendor, sample, derivedFromMappingId, evidence);
    }

    public MappingRevision forkForTenant(String targetTenantId, Instant now) {
        if (scope != MappingScope.GLOBAL) return newDraft(now);
        UUID draftId = UUID.randomUUID();
        return new MappingRevision(draftId, targetTenantId, name, 1, sourceSchemaId, targetSchemaId,
                MappingStatus.DRAFT, nodes, null, now, null, draftId, null, null, 1,
                MappingScope.TENANT, null, sample, id);
    }

    @JsonProperty(value = "compatibility", access = JsonProperty.Access.READ_ONLY)
    public MappingCompatibility compatibility() {
        return MappingCompatibilityRegistry.resolve(sourceSchemaId, targetSchemaId);
    }
}
