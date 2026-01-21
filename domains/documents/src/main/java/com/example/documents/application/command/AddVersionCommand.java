package com.example.documents.application.command;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;

import java.util.Objects;

/**
 * Command to add a new version to an existing document.
 *
 * @param documentSetId the identifier of the document set
 * @param documentId the identifier of the document to add a version to
 * @param content the content of the new version
 * @param createdBy identifier of the user creating the version
 */
public record AddVersionCommand(
        DocumentSetId documentSetId,
        DocumentId documentId,
        Content content,
        String createdBy
) {
    public AddVersionCommand {
        Objects.requireNonNull(documentSetId, "Document set ID cannot be null");
        Objects.requireNonNull(documentId, "Document ID cannot be null");
        Objects.requireNonNull(content, "Content cannot be null");
        Objects.requireNonNull(createdBy, "Created by cannot be null");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("Created by cannot be blank");
        }
    }

    /**
     * Creates a new AddVersionCommand.
     */
    public static AddVersionCommand of(
            DocumentSetId documentSetId,
            DocumentId documentId,
            Content content,
            String createdBy) {
        return new AddVersionCommand(documentSetId, documentId, content, createdBy);
    }
}
