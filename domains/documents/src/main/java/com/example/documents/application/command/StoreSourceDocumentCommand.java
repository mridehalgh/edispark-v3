package com.example.documents.application.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.DocumentType;

public record StoreSourceDocumentCommand(
        DocumentType documentType,
        Content content,
        String createdBy,
        Map<String, String> metadata,
        String parseStatus,
        String messageType,
        List<String> parseErrors) {

    public StoreSourceDocumentCommand {
        Objects.requireNonNull(documentType, "Document type cannot be null");
        Objects.requireNonNull(content, "Content cannot be null");
        Objects.requireNonNull(createdBy, "Created by cannot be null");
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("Created by cannot be blank");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        parseErrors = parseErrors == null ? List.of() : List.copyOf(parseErrors);
    }
}
