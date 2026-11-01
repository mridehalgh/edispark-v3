package com.edispark.transformations.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MappingRevision(UUID id, String tenantId, String name, int revision, UUID sourceSchemaId,
        UUID targetSchemaId, MappingStatus status, List<MappingNode> nodes, String contentHash,
        Instant createdAt, Instant publishedAt, UUID familyId, Instant scheduledAt, Instant archivedAt,
        long version, MappingScope scope, String vendor, MappingSample sample, UUID derivedFromMappingId) {
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
        return new MappingRevision(id, tenantId, name, revision, sourceSchemaId, targetSchemaId,
                MappingStatus.PUBLISHED, nodes, hash, createdAt, now, familyId, null, null, version + 1);
    }

    public MappingRevision schedule(String hash, Instant effectiveAt) {
        if (status != MappingStatus.DRAFT) throw new IllegalStateException("Only draft mappings can be scheduled");
        if (nodes.isEmpty()) throw new IllegalStateException("A mapping must contain at least one node");
        return new MappingRevision(id, tenantId, name, revision, sourceSchemaId, targetSchemaId,
                MappingStatus.SCHEDULED, nodes, hash, createdAt, null, familyId, effectiveAt, null, version + 1);
    }

    public MappingRevision publishDue(Instant now) {
        if (status != MappingStatus.SCHEDULED || scheduledAt.isAfter(now)) return this;
        return new MappingRevision(id, tenantId, name, revision, sourceSchemaId, targetSchemaId,
                MappingStatus.PUBLISHED, nodes, contentHash, createdAt, now, familyId, null, null, version + 1);
    }

    public MappingRevision revise(List<MappingNode> revisedNodes) {
        if (status != MappingStatus.DRAFT) throw new IllegalStateException("Published mappings are immutable");
        return new MappingRevision(id, tenantId, name, revision, sourceSchemaId, targetSchemaId,
                status, revisedNodes, null, createdAt, null, familyId, null, null, version + 1);
    }

    public MappingRevision newDraft(Instant now) {
        if (status != MappingStatus.PUBLISHED) throw new IllegalStateException("Only published mappings can be revised");
        return new MappingRevision(UUID.randomUUID(), tenantId, name, revision + 1, sourceSchemaId, targetSchemaId,
                MappingStatus.DRAFT, nodes, null, now, null, familyId, null, null, 1);
    }

    public MappingRevision archive(Instant now) {
        if (status == MappingStatus.DRAFT || status == MappingStatus.SCHEDULED) {
            throw new IllegalStateException("Discard the draft or cancel its schedule instead");
        }
        return new MappingRevision(id, tenantId, name, revision, sourceSchemaId, targetSchemaId,
                MappingStatus.ARCHIVED, nodes, contentHash, createdAt, publishedAt, familyId, null, now, version + 1);
    }

    public MappingRevision forkForTenant(String targetTenantId, Instant now) {
        if (scope != MappingScope.GLOBAL) return newDraft(now);
        UUID draftId = UUID.randomUUID();
        return new MappingRevision(draftId, targetTenantId, name, 1, sourceSchemaId, targetSchemaId,
                MappingStatus.DRAFT, nodes, null, now, null, draftId, null, null, 1,
                MappingScope.TENANT, null, sample, id);
    }
}
