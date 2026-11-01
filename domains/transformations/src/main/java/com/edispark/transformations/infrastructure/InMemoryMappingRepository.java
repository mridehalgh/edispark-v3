package com.edispark.transformations.infrastructure;

import com.edispark.transformations.domain.MappingRepository;
import com.edispark.transformations.domain.MappingRevision;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "openapi-export"})
public final class InMemoryMappingRepository implements MappingRepository {
    private final Map<UUID, MappingRevision> mappings = new ConcurrentHashMap<>();
    @Override public MappingRevision save(MappingRevision mapping) { mappings.put(mapping.id(), mapping); return mapping; }
    @Override public Optional<MappingRevision> find(String tenantId, UUID id) { return Optional.ofNullable(mappings.get(id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public List<MappingRevision> list(String tenantId) { return mappings.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList(); }
    @Override public void delete(String tenantId, UUID id) { mappings.computeIfPresent(id, (key, value) -> value.tenantId().equals(tenantId) ? null : value); }
}
