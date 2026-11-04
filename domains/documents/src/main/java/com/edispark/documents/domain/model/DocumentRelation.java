package com.edispark.documents.domain.model;

import java.util.Objects;

/** A typed link from a document to another document in the same document set. */
public record DocumentRelation(DocumentRelationType type, DocumentId targetDocumentId) {
    public DocumentRelation {
        Objects.requireNonNull(type, "Relation type cannot be null");
        Objects.requireNonNull(targetDocumentId, "Relation target cannot be null");
    }
}
