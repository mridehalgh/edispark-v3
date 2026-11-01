package com.edispark.transformations.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MappingRevision(UUID id, String tenantId, String name, int revision, UUID sourceSchemaId,
        UUID targetSchemaId, MappingStatus status, List<MappingNode> nodes, String contentHash,
        Instant createdAt, Instant publishedAt) {
    private static final int MAX_NODES = 2_000;

    public MappingRevision {
        Objects.requireNonNull(id);
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (name == null || name.isBlank() || name.length() > 160) throw new IllegalArgumentException("name is invalid");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        Objects.requireNonNull(sourceSchemaId);
        Objects.requireNonNull(targetSchemaId);
        Objects.requireNonNull(status);
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        if (nodes.size() > MAX_NODES) throw new IllegalArgumentException("Mapping exceeds 2000 nodes");
        Objects.requireNonNull(createdAt);
        if (status == MappingStatus.PUBLISHED && (contentHash == null || publishedAt == null)) {
            throw new IllegalArgumentException("Published mappings require a hash and timestamp");
        }
    }

    public static MappingRevision draft(String tenantId, String name, UUID sourceSchemaId, UUID targetSchemaId,
                                        List<MappingNode> nodes, Instant now) {
        return new MappingRevision(UUID.randomUUID(), tenantId, name, 1, sourceSchemaId, targetSchemaId,
                MappingStatus.DRAFT, nodes, null, now, null);
    }

    public MappingRevision publish(String hash, Instant now) {
        if (status != MappingStatus.DRAFT) throw new IllegalStateException("Only draft mappings can be published");
        if (nodes.isEmpty()) throw new IllegalStateException("A mapping must contain at least one node");
        return new MappingRevision(id, tenantId, name, revision, sourceSchemaId, targetSchemaId,
                MappingStatus.PUBLISHED, nodes, hash, createdAt, now);
    }

    public MappingRevision revise(List<MappingNode> revisedNodes) {
        if (status != MappingStatus.DRAFT) throw new IllegalStateException("Published mappings are immutable");
        return new MappingRevision(id, tenantId, name, revision, sourceSchemaId, targetSchemaId,
                status, revisedNodes, null, createdAt, null);
    }

    public MappingRevision newDraft(Instant now) {
        if (status != MappingStatus.PUBLISHED) throw new IllegalStateException("Only published mappings can be revised");
        return new MappingRevision(UUID.randomUUID(), tenantId, name, revision + 1, sourceSchemaId, targetSchemaId,
                MappingStatus.DRAFT, nodes, null, now, null);
    }
}
