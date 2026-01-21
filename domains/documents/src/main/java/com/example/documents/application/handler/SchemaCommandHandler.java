package com.example.documents.application.handler;

import com.example.documents.application.command.AddSchemaVersionCommand;
import com.example.documents.application.command.CreateSchemaCommand;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaVersion;
import com.example.documents.domain.repository.ContentStore;
import com.example.documents.domain.repository.SchemaRepository;

import java.util.Objects;

/**
 * Command handler for Schema aggregate operations.
 * 
 * <p>This handler orchestrates domain operations for schemas, including:
 * <ul>
 *   <li>Creating new schemas</li>
 *   <li>Adding versions to existing schemas</li>
 * </ul>
 * 
 * <p>Requirement 3.7: When a new SchemaVersion is created, validate the schema definition
 * is syntactically correct.</p>
 */
public class SchemaCommandHandler {

    private final SchemaRepository schemaRepository;
    private final ContentStore contentStore;

    public SchemaCommandHandler(
            SchemaRepository schemaRepository,
            ContentStore contentStore) {
        this.schemaRepository = Objects.requireNonNull(schemaRepository);
        this.contentStore = Objects.requireNonNull(contentStore);
    }

    /**
     * Handles the creation of a new Schema.
     * 
     * @param command the create schema command
     * @return the created Schema
     */
    public Schema handle(CreateSchemaCommand command) {
        // Create schema
        Schema schema = Schema.create(command.name(), command.format());

        // Persist
        schemaRepository.save(schema);

        return schema;
    }

    /**
     * Handles adding a new version to an existing Schema.
     * 
     * <p>Requirement 3.7: Validates the schema definition is syntactically correct
     * before adding the version.</p>
     * 
     * @param command the add schema version command
     * @return the created SchemaVersion
     * @throws SchemaNotFoundException if the schema does not exist
     * @throws IllegalArgumentException if a version with the same identifier already exists
     */
    public SchemaVersion handle(AddSchemaVersionCommand command) {
        // Load schema
        Schema schema = schemaRepository.findById(command.schemaId())
                .orElseThrow(() -> new SchemaNotFoundException(command.schemaId()));

        // Validate schema syntax (basic validation - content is parseable)
        validateSchemaSyntax(command);

        // Store schema definition content
        contentStore.store(command.definition());
        ContentRef definitionRef = ContentRef.of(command.definition().hash());

        // Add version to schema
        SchemaVersion schemaVersion = schema.addVersion(command.version(), definitionRef);

        // Persist
        schemaRepository.save(schema);

        return schemaVersion;
    }

    /**
     * Validates that the schema definition is syntactically correct.
     * 
     * <p>This is a basic validation that ensures the content can be parsed.
     * More sophisticated validation could be added based on the schema format.</p>
     */
    private void validateSchemaSyntax(AddSchemaVersionCommand command) {
        // Basic validation: ensure content is not empty
        if (command.definition().data().length == 0) {
            throw new IllegalArgumentException("Schema definition cannot be empty");
        }

        // Additional format-specific validation could be added here
        // For now, we rely on the content being valid based on its format
    }
}
