package com.example.documents.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a document version.
 *
 * @param id the unique identifier of the version
 * @param versionNumber the sequential version number
 * @param contentHash the hash of the version content
 * @param createdAt the timestamp when the version was created
 * @param createdBy the identifier of the user who created the version
 */
public record DocumentVersionResponse(
    UUID id,
    int versionNumber,
    String contentHash,
    Instant createdAt,
    String createdBy
) {}
