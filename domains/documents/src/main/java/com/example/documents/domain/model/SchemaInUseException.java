package com.example.documents.domain.model;

/**
 * Exception thrown when attempting to delete a schema version that is still referenced by documents.
 * 
 * <p>Schema versions cannot be deleted if they are referenced by existing documents,
 * as this would break referential integrity.</p>
 */
public class SchemaInUseException extends RuntimeException {

    private final SchemaId schemaId;
    private final VersionIdentifier versionIdentifier;

    public SchemaInUseException(SchemaId schemaId, VersionIdentifier versionIdentifier) {
        super("Schema version " + versionIdentifier + " of schema " + schemaId + 
              " cannot be deleted because it is still referenced by documents");
        this.schemaId = schemaId;
        this.versionIdentifier = versionIdentifier;
    }

    public SchemaInUseException(SchemaVersionRef schemaVersionRef) {
        this(schemaVersionRef.schemaId(), schemaVersionRef.version());
    }

    public SchemaId schemaId() {
        return schemaId;
    }

    public VersionIdentifier versionIdentifier() {
        return versionIdentifier;
    }
}