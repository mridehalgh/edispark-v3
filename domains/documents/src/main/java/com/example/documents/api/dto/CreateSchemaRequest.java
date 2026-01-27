package com.example.documents.api.dto;

import com.example.documents.domain.model.SchemaFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new schema.
 *
 * @param name the name of the schema
 * @param format the format type of the schema (XSD, JSON_SCHEMA, RELAXNG)
 */
public record CreateSchemaRequest(
    @NotBlank String name,
    @NotNull SchemaFormat format
) {}
