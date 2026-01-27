package com.example.documents.api.dto;

import com.example.documents.domain.model.DocumentType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO representing a document set with its documents.
 *
 * @param id the unique identifier of the document set
 * @param createdAt the timestamp when the document set was created
 * @param createdBy the identifier of the user who created the document set
 * @param metadata optional metadata key-value pairs associated with the document set
 * @param documents the list of documents in the set
 */
public record DocumentSetResponse(
    UUID id,
    Instant createdAt,
    String createdBy,
    Map<String, String> metadata,
    List<DocumentSummary> documents
) {
    /**
     * Summary information about a document within a document set.
     *
     * @param id the unique identifier of the document
     * @param type the type of the document
     * @param versionCount the total number of versions for this document
     */
    public record DocumentSummary(
        UUID id,
        DocumentType type,
        int versionCount
    ) {}
}
