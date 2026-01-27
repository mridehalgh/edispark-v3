package com.example.documents.api.dto;

import com.example.documents.domain.model.Format;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a document derivative.
 *
 * @param id the unique identifier of the derivative
 * @param sourceVersionId the ID of the source version this derivative was created from
 * @param targetFormat the format of the derivative
 * @param contentHash the hash of the derivative content
 * @param transformationMethod the method used to transform the document
 * @param createdAt the timestamp when the derivative was created
 */
public record DerivativeResponse(
    UUID id,
    UUID sourceVersionId,
    Format targetFormat,
    String contentHash,
    String transformationMethod,
    Instant createdAt
) {}
