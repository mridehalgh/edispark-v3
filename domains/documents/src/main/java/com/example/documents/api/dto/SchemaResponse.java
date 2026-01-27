package com.example.documents.api.dto;

import com.example.documents.domain.model.SchemaFormat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO representing a schema with its versions.
 *
 * @param id the unique identifier of the schema
 * @param name the name of the schema
 * @param format the format type of the schema (XSD, JSON_SCHEMA, RELAXNG)
 * @param versions the list of version summaries for this schema
 */
public record SchemaResponse(
    UUID id,
    String name,
    SchemaFormat format,
    List<SchemaVersionSummary> versions
) {
    /**
     * Summary information about a schema version.
     *
     * @param id the unique identifier of the version
     * @param versionIdentifier the semantic version identifier (e.g. "1.0.0")
     * @param createdAt the timestamp when the version was created
     * @param deprecated whether this version has been deprecated
     */
    public record SchemaVersionSummary(
        UUID id,
        String versionIdentifier,
        Instant createdAt,
        boolean deprecated
    ) {}
}
