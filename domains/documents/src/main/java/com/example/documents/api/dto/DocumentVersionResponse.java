package com.example.documents.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.documents.domain.model.Format;

/**
 * Response DTO representing a document version.
 *
 * @param id the unique identifier of the version
 * @param versionNumber the sequential version number
 * @param contentHash the hash of the version content
 * @param createdAt the timestamp when the version was created
 * @param createdBy the identifier of the user who created the version
 * @param format the serialisation format of the stored content
 * @param parseStatus the recorded parse status for source content, when available
 * @param messageType the identified EDI message type, when available
 * @param parseErrors the recorded parse errors, if any
 */
public record DocumentVersionResponse(
    UUID id,
    int versionNumber,
    String contentHash,
    Instant createdAt,
    String createdBy,
    Format format,
    String parseStatus,
    String messageType,
    List<String> parseErrors
) {
    public DocumentVersionResponse {
        parseErrors = parseErrors == null ? List.of() : List.copyOf(parseErrors);
    }
}
