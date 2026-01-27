package com.example.documents.api.dto;

import com.example.documents.domain.model.Format;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a derivative of a document version.
 *
 * @param sourceVersionNumber the version number of the source document (must be at least 1)
 * @param targetFormat the target format for the derivative
 */
public record CreateDerivativeRequest(
    @Min(1) int sourceVersionNumber,
    @NotNull Format targetFormat
) {}
