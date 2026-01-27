package com.example.documents.api.dto;

import java.util.UUID;

/**
 * Response DTO representing a reference to a schema and its version.
 *
 * @param schemaId the unique identifier of the schema
 * @param version the version identifier of the schema
 */
public record SchemaRefResponse(
    UUID schemaId,
    String version
) {}
