package com.example.documents.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for adding a new version to an existing schema.
 *
 * @param versionIdentifier the version identifier (e.g., "1.0.0", "2.1.0")
 * @param definition the schema definition content, Base64 encoded
 */
public record AddSchemaVersionRequest(
    @NotBlank String versionIdentifier,
    @NotBlank String definition
) {}
