package com.example.documents.domain.model;

import java.util.Objects;

/**
 * A reference to a specific version of a schema.
 */
public record SchemaVersionRef(SchemaId schemaId, VersionIdentifier version) {

    public SchemaVersionRef {
        Objects.requireNonNull(schemaId, "Schema ID cannot be null");
        Objects.requireNonNull(version, "Version cannot be null");
    }

    /**
     * Creates a SchemaVersionRef from schema ID and version.
     */
    public static SchemaVersionRef of(SchemaId schemaId, VersionIdentifier version) {
        return new SchemaVersionRef(schemaId, version);
    }

    @Override
    public String toString() {
        return schemaId + "@" + version;
    }
}
