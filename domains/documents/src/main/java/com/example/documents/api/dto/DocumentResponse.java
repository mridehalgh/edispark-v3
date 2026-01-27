package com.example.documents.api.dto;

import com.example.documents.domain.model.DocumentType;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO representing a document with its current version and derivatives.
 *
 * @param id the unique identifier of the document
 * @param type the type of the document
 * @param schemaRef the reference to the schema used for validation
 * @param versionCount the total number of versions for this document
 * @param currentVersion the current (latest) version of the document
 * @param derivatives the list of derivatives created from this document
 */
public record DocumentResponse(
    UUID id,
    DocumentType type,
    SchemaRefResponse schemaRef,
    int versionCount,
    DocumentVersionResponse currentVersion,
    List<DerivativeResponse> derivatives
) {}
