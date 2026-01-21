package com.example.documents.application.command;

import com.example.documents.domain.model.SchemaFormat;

import java.util.Objects;

/**
 * Command to create a new schema.
 *
 * @param name the human-readable name of the schema
 * @param format the format type of the schema (XSD, JSON_SCHEMA, etc.)
 */
public record CreateSchemaCommand(
        String name,
        SchemaFormat format
) {
    public CreateSchemaCommand {
        Objects.requireNonNull(name, "Schema name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be blank");
        }
        Objects.requireNonNull(format, "Schema format cannot be null");
    }

    /**
     * Creates a new CreateSchemaCommand.
     */
    public static CreateSchemaCommand of(String name, SchemaFormat format) {
        return new CreateSchemaCommand(name, format);
    }
}
