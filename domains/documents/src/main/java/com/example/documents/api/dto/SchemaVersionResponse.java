package com.example.documents.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a schema version.
 *
 * @param id the unique identifier of the version
 * @param versionIdentifier the semantic version identifier (e.g. "1.0.0")
 * @param createdAt the timestamp when the version was created
 * @param deprecated whether this version has been deprecated
 */
public record SchemaVersionResponse(
    UUID id,
    String versionIdentifier,
    Instant createdAt,
    boolean deprecated
) {}
