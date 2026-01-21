package com.example.documents.application.command;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.SchemaVersionRef;

import java.util.Map;
import java.util.Objects;

/**
 * Command to create a new DocumentSet with an initial document.
 *
 * @param initialDocumentType the type of the initial document
 * @param schemaRef reference to the schema version for validation
 * @param initialContent the content of the initial document
 * @param createdBy identifier of the user creating the document set
 * @param metadata optional key-value metadata for the document set
 */
public record CreateDocumentSetCommand(
        DocumentType initialDocumentType,
        SchemaVersionRef schemaRef,
        Content initialContent,
        String createdBy,
        Map<String, String> metadata
) {
    public CreateDocumentSetCommand {
        Objects.requireNonNull(initialDocumentType, "Initial document type cannot be null");
        Objects.requireNonNull(schemaRef, "Schema reference cannot be null");
        Objects.requireNonNull(initialContent, "Initial content cannot be null");
        Objects.requireNonNull(createdBy, "Created by cannot be null");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("Created by cannot be blank");
        }
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a command with no metadata.
     */
    public static CreateDocumentSetCommand of(
            DocumentType documentType,
            SchemaVersionRef schemaRef,
            Content content,
            String createdBy) {
        return new CreateDocumentSetCommand(documentType, schemaRef, content, createdBy, Map.of());
    }
}
