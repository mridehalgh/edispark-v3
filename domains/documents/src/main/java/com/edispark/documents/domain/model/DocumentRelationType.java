package com.edispark.documents.domain.model;

/** Describes why two documents in a business document chain are connected. */
public enum DocumentRelationType {
    RELATED_TO,
    RESPONDS_TO,
    DESPATCHES,
    RECEIVES,
    INVOICES,
    REMITS,
    CORRECTS,
    SUPERSEDES
}
