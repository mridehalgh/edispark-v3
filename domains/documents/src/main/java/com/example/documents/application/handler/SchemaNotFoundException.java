package com.example.documents.application.handler;

import com.example.documents.domain.model.SchemaId;

/**
 * Exception thrown when a Schema cannot be found.
 */
public class SchemaNotFoundException extends RuntimeException {

    private final SchemaId schemaId;

    public SchemaNotFoundException(SchemaId schemaId) {
        super("Schema not found: " + schemaId);
        this.schemaId = schemaId;
    }

    public SchemaId schemaId() {
        return schemaId;
    }
}
