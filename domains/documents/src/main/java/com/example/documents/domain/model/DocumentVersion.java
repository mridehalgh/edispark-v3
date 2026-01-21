package com.example.documents.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable version of a document.
 * 
 * <p>Each version captures a specific revision of a document's content at a point in time.
 * Versions are immutable once created and form a chain through the previousVersion reference.</p>
 * 
 * <p>Requirements: 2.1, 2.2, 2.3, 2.7</p>
 */
public final class DocumentVersion {

    private final DocumentVersionId id;
    private final int versionNumber;
    private final ContentRef contentRef;
    private final ContentHash contentHash;
    private final Instant createdAt;
    private final String createdBy;
    private final DocumentVersionId previousVersion;

    private DocumentVersion(
            DocumentVersionId id,
            int versionNumber,
            ContentRef contentRef,
            ContentHash contentHash,
            Instant createdAt,
            String createdBy,
            DocumentVersionId previousVersion) {
        this.id = Objects.requireNonNull(id, "Version ID cannot be null");
        this.contentRef = Objects.requireNonNull(contentRef, "Content reference cannot be null");
        this.contentHash = Objects.requireNonNull(contentHash, "Content hash cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "Created by cannot be null");
        
        if (versionNumber < 1) {
            throw new IllegalArgumentException("Version number must be at least 1");
        }
        this.versionNumber = versionNumber;
        
        // First version must not have a previous version, subsequent versions must have one
        if (versionNumber == 1 && previousVersion != null) {
            throw new IllegalArgumentException("First version cannot have a previous version");
        }
        if (versionNumber > 1 && previousVersion == null) {
            throw new IllegalArgumentException("Version " + versionNumber + " must reference a previous version");
        }
        this.previousVersion = previousVersion;
    }

    /**
     * Creates the first version of a document.
     */
    public static DocumentVersion createFirst(
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy) {
        return new DocumentVersion(
                DocumentVersionId.generate(),
                1,
                contentRef,
                contentHash,
                Instant.now(),
                createdBy,
                null);
    }

    /**
     * Creates a subsequent version of a document.
     */
    public static DocumentVersion createNext(
            DocumentVersion previousVersion,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy) {
        Objects.requireNonNull(previousVersion, "Previous version cannot be null");
        return new DocumentVersion(
                DocumentVersionId.generate(),
                previousVersion.versionNumber() + 1,
                contentRef,
                contentHash,
                Instant.now(),
                createdBy,
                previousVersion.id());
    }

    /**
     * Creates a DocumentVersion with all fields specified (for reconstruction from persistence).
     */
    public static DocumentVersion reconstitute(
            DocumentVersionId id,
            int versionNumber,
            ContentRef contentRef,
            ContentHash contentHash,
            Instant createdAt,
            String createdBy,
            DocumentVersionId previousVersion) {
        return new DocumentVersion(id, versionNumber, contentRef, contentHash, createdAt, createdBy, previousVersion);
    }

    public DocumentVersionId id() {
        return id;
    }

    public int versionNumber() {
        return versionNumber;
    }

    public ContentRef contentRef() {
        return contentRef;
    }

    public ContentHash contentHash() {
        return contentHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public DocumentVersionId previousVersion() {
        return previousVersion;
    }

    /**
     * Returns true if this is the first version.
     */
    public boolean isFirstVersion() {
        return versionNumber == 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentVersion that = (DocumentVersion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DocumentVersion[id=" + id + ", versionNumber=" + versionNumber + ", contentHash=" + contentHash + "]";
    }
}
