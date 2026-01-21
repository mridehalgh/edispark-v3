package com.example.documents.application.handler;

import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.repository.SchemaRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of SchemaRepository for testing.
 */
class InMemorySchemaRepository implements SchemaRepository {

    private final Map<SchemaId, Schema> store = new HashMap<>();

    @Override
    public Optional<Schema> findById(SchemaId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void save(Schema schema) {
        store.put(schema.id(), schema);
    }

    @Override
    public boolean isVersionReferenced(SchemaVersionRef ref) {
        // For testing, always return false
        return false;
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
