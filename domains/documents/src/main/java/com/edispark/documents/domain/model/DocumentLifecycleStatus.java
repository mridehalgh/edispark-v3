package com.edispark.documents.domain.model;

/** The business lifecycle of a document, independent of exchange delivery state. */
public enum DocumentLifecycleStatus {
    DRAFT,
    FINAL,
    CANCELLED
}
