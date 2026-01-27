package com.example.documents.api.dto;

import com.example.documents.domain.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for adding a document to an existing document set.
 *
 * @param documentType the type of the document
 * @param schemaId the ID of the schema to validate against
 * @param schemaVersion the version identifier of the schema
 * @param content the document content, Base64 encoded
 * @param createdBy the identifier of the user adding the document
 * @param relatedDocumentId optional ID of a related document within the set
 */
public record AddDocumentRequest(
    @NotNull DocumentType documentType,
    @NotNull UUID schemaId,
    @NotBlank String schemaVersion,
    @NotBlank String content,
    @NotBlank String createdBy,
    UUID relatedDocumentId
) {}
