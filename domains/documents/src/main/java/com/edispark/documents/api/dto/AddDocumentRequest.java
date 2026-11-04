package com.edispark.documents.api.dto;

import com.edispark.documents.domain.model.DocumentType;
import com.edispark.documents.domain.model.DocumentLifecycleStatus;
import com.edispark.documents.domain.model.DocumentRelationType;
import com.edispark.documents.domain.model.Format;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for adding a document to an existing document set.
 *
 * @param documentType the type of the document
 * @param schemaId the ID of the schema to validate against
 * @param schemaVersion the version identifier of the schema
 * @param content the document content, Base64 encoded
 * @param createdBy the identifier of the user adding the document
 * @param relatedDocumentId optional ID of a related document within the set
 */
public record AddDocumentRequest(
    @NotNull DocumentType documentType,
    @NotNull UUID schemaId,
    @NotBlank String schemaVersion,
    @NotBlank String content,
    @NotBlank String createdBy,
    UUID relatedDocumentId,
    DocumentRelationType relationType,
    Format format,
    DocumentLifecycleStatus lifecycleStatus
) {
    public AddDocumentRequest(
            DocumentType documentType,
            UUID schemaId,
            String schemaVersion,
            String content,
            String createdBy,
            UUID relatedDocumentId) {
        this(documentType, schemaId, schemaVersion, content, createdBy, relatedDocumentId,
                DocumentRelationType.RELATED_TO, Format.XML, DocumentLifecycleStatus.FINAL);
    }
}
