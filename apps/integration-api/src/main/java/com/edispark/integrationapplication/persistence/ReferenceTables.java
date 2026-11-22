package com.edispark.integrationapplication.persistence;

import com.edispark.transformations.domain.ReferenceTableRevision;
import com.edispark.transformations.domain.ReferenceTableReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cottn.core.tenancy.TenantAccess;
import java.util.UUID;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!openapi-export")
public final class ReferenceTables implements ReferenceTableReader {
    private static final String PREFIX = "REFERENCE_TABLE#";
    private static final int MAX_STORAGE_BYTES = 300_000;
    private final IntegrationDynamoStore store;
    private final ObjectMapper mapper;

    ReferenceTables(IntegrationDynamoStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Override
    public ReferenceTableRevision require(UUID id) {
        return store.get(TenantAccess.currentId(), PREFIX + id).map(this::decode)
                .orElseThrow(() -> new IllegalArgumentException("The reference table revision is unavailable."));
    }

    public List<Summary> list() {
        return store.list(TenantAccess.currentId(), PREFIX).stream().map(this::decode)
                .map(table -> new Summary(table.id(), table.previousRevisionId(), table.name(), table.keyColumn(),
                        table.rows().getFirst().keySet().stream().sorted().toList(), table.rows().size())).toList();
    }

    public record Summary(UUID id, UUID previousRevisionId, String name, String keyColumn,
                          List<String> columns, int rowCount) { }

    public ReferenceTableRevision publish(ReferenceTableRevision revision) {
        if (revision.previousRevisionId() != null) require(revision.previousRevisionId());
        String value = encode(revision);
        if (!store.putVersionedIfCurrent(TenantAccess.currentId(), PREFIX + revision.id(),
                "REFERENCE_TABLE", value, 0) && !require(revision.id()).equals(revision)) {
            throw new IllegalStateException("The reference table revision already exists with different values.");
        }
        return revision;
    }

    private String encode(ReferenceTableRevision revision) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(revision);
            if (bytes.length > MAX_STORAGE_BYTES) throw new IllegalArgumentException("Reference table exceeds 300 KB.");
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode reference table revision.", exception);
        }
    }

    private ReferenceTableRevision decode(String value) {
        try {
            return mapper.readValue(value, ReferenceTableRevision.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read reference table revision.", exception);
        }
    }
}
