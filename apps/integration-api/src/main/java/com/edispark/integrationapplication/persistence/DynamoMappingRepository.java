package com.edispark.integrationapplication.persistence;

import com.edispark.transformations.domain.MappingRepository;
import com.edispark.transformations.domain.MappingRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local & !openapi-export")
final class DynamoMappingRepository implements MappingRepository {
    private final IntegrationDynamoStore store;
    private final ObjectMapper json;
    DynamoMappingRepository(IntegrationDynamoStore store, ObjectMapper json) { this.store = store; this.json = json; }
    @Override public MappingRevision save(MappingRevision mapping) {
        store.put(mapping.tenantId(), "MAPPING#" + mapping.id(), "MAPPING", write(mapping)); return mapping;
    }
    @Override public Optional<MappingRevision> find(String tenantId, UUID id) {
        return store.get(tenantId, "MAPPING#" + id).map(this::read);
    }
    @Override public List<MappingRevision> list(String tenantId) {
        return store.list(tenantId, "MAPPING#").stream().map(this::read).toList();
    }
    @Override public void delete(String tenantId, UUID id) { store.delete(tenantId, "MAPPING#" + id); }
    private String write(MappingRevision value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Could not serialize mapping", e); } }
    private MappingRevision read(String value) { try { return json.readValue(value, MappingRevision.class); } catch (Exception e) { throw new IllegalStateException("Could not read mapping", e); } }
}
