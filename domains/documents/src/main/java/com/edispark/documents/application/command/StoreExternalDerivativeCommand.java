package com.edispark.documents.application.command;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.DocumentId;
import com.edispark.documents.domain.model.DocumentSetId;

public record StoreExternalDerivativeCommand(DocumentSetId documentSetId, DocumentId documentId,
        int sourceVersionNumber, Content content, String variantKey) {
    public StoreExternalDerivativeCommand {
        if (sourceVersionNumber < 1) throw new IllegalArgumentException("sourceVersionNumber must be positive");
        if (content == null) throw new IllegalArgumentException("content is required");
        if (variantKey == null || variantKey.isBlank()) throw new IllegalArgumentException("variantKey is required");
    }
}
