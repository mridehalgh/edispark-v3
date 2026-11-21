package com.edispark.transformations.infrastructure;

import io.cottn.core.tenancy.TenantAccess;

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
@Profile("openapi-export")
public final class InMemoryMappingRepository implements MappingRepository, MappingCreationAdapter {
    private final Map<String, MappingRevision> mappings = new ConcurrentHashMap<>();
    @Override public MappingRevision save(MappingRevision mapping) { TenantAccess.requireOwner(mapping.tenantId()); mappings.put(mapping.tenantId() + ":" + mapping.id(), mapping); return mapping; }
    @Override public Optional<MappingRevision> find(UUID id) { String tenantId = TenantAccess.currentId(); return Optional.ofNullable(mappings.get(tenantId + ":" + id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public List<MappingRevision> list() { String tenantId = TenantAccess.currentId(); return mappings.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList(); }
    @Override public void delete(UUID id) { String tenantId = TenantAccess.currentId(); mappings.computeIfPresent(tenantId + ":" + id, (key, value) -> value.tenantId().equals(tenantId) ? null : value); }
}
