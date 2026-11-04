package com.edispark.documents.application.command;

import com.edispark.documents.domain.model.DocumentId;
import com.edispark.documents.domain.model.DocumentSetId;

import java.util.Objects;

public record FinalizeDocumentCommand(DocumentSetId documentSetId, DocumentId documentId) {
    public FinalizeDocumentCommand {
        Objects.requireNonNull(documentSetId, "Document set ID cannot be null");
        Objects.requireNonNull(documentId, "Document ID cannot be null");
    }
}
