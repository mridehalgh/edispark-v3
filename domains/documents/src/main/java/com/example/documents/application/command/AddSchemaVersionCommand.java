package com.example.documents.application.command;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.VersionIdentifier;

import java.util.Objects;

/**
 * Command to add a new version to an existing schema.
 *
 * @param schemaId the identifier of the schema
 * @param version the version identifier (e.g., "2.1.0")
 * @param definition the schema definition content
 */
public record AddSchemaVersionCommand(
        SchemaId schemaId,
        VersionIdentifier version,
        Content definition
) {
    public AddSchemaVersionCommand {
        Objects.requireNonNull(schemaId, "Schema ID cannot be null");
        Objects.requireNonNull(version, "Version identifier cannot be null");
        Objects.requireNonNull(definition, "Schema definition cannot be null");
    }

    /**
     * Creates a new AddSchemaVersionCommand.
     */
    public static AddSchemaVersionCommand of(
            SchemaId schemaId,
            VersionIdentifier version,
            Content definition) {
        return new AddSchemaVersionCommand(schemaId, version, definition);
    }
}
