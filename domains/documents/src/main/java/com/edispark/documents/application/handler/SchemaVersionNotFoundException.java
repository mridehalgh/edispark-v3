package com.edispark.documents.application.handler;

import com.edispark.documents.domain.model.SchemaVersionRef;

/**
 * Exception thrown when a SchemaVersion cannot be found.
 */
public class SchemaVersionNotFoundException extends RuntimeException {

    private final SchemaVersionRef schemaVersionRef;

    public SchemaVersionNotFoundException(SchemaVersionRef schemaVersionRef) {
        super("Schema version not found: " + schemaVersionRef);
        this.schemaVersionRef = schemaVersionRef;
    }

    public SchemaVersionRef schemaVersionRef() {
        return schemaVersionRef;
    }
}
