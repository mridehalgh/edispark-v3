package com.example.documents.api.dto;

import com.example.documents.domain.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for creating a new document set with an initial document.
 *
 * @param documentType the type of the initial document
 * @param schemaId the ID of the schema to validate against
 * @param schemaVersion the version identifier of the schema
 * @param content the document content, Base64 encoded
 * @param createdBy the identifier of the user creating the document set
 * @param metadata optional metadata key-value pairs for the document set
 */
public record CreateDocumentSetRequest(
    @NotNull DocumentType documentType,
    @NotNull UUID schemaId,
    @NotBlank String schemaVersion,
    @NotBlank String content,
    @NotBlank String createdBy,
    Map<String, String> metadata
) {}
