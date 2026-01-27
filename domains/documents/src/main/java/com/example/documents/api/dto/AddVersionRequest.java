package com.example.documents.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for adding a new version to an existing document.
 *
 * @param content the document content, Base64 encoded
 * @param createdBy the identifier of the user adding the version
 */
public record AddVersionRequest(
    @NotBlank String content,
    @NotBlank String createdBy
) {}
