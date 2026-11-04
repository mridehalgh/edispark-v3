package com.edispark.documents.domain.model;

import java.util.Objects;

/** A typed link to a document. A null target set denotes the containing set. */
public record DocumentRelation(DocumentRelationType type, DocumentId targetDocumentId,
        DocumentSetId targetDocumentSetId) {
    public DocumentRelation(DocumentRelationType type, DocumentId targetDocumentId) {
        this(type, targetDocumentId, null);
    }
    public DocumentRelation {
        Objects.requireNonNull(type, "Relation type cannot be null");
        Objects.requireNonNull(targetDocumentId, "Relation target cannot be null");
    }
}
