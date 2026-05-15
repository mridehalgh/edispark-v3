package com.example.documents.domain.model;

import java.time.Instant;
import java.util.List;
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
    private final Format format;
    private final Instant createdAt;
    private final String createdBy;
    private final DocumentVersionId previousVersion;
    private final String parseStatus;
    private final String messageType;
    private final List<String> parseErrors;

    private DocumentVersion(
            DocumentVersionId id,
            int versionNumber,
            ContentRef contentRef,
            ContentHash contentHash,
            Format format,
            Instant createdAt,
            String createdBy,
            DocumentVersionId previousVersion,
            String parseStatus,
            String messageType,
            List<String> parseErrors) {
        this.id = Objects.requireNonNull(id, "Version ID cannot be null");
        this.contentRef = Objects.requireNonNull(contentRef, "Content reference cannot be null");
        this.contentHash = Objects.requireNonNull(contentHash, "Content hash cannot be null");
        this.format = Objects.requireNonNull(format, "Format cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "Created by cannot be null");
        this.parseStatus = parseStatus;
        this.messageType = messageType;
        this.parseErrors = List.copyOf(parseErrors == null ? List.of() : parseErrors);
        
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
        return createFirst(contentRef, contentHash, Format.XML, createdBy, null, null, List.of());
    }

    public static DocumentVersion createFirst(
            ContentRef contentRef,
            ContentHash contentHash,
            Format format,
            String createdBy,
            String parseStatus,
            String messageType,
            List<String> parseErrors) {
        return new DocumentVersion(
                DocumentVersionId.generate(),
                1,
                contentRef,
                contentHash,
                format,
                Instant.now(),
                createdBy,
                null,
                parseStatus,
                messageType,
                parseErrors);
    }

    /**
     * Creates a subsequent version of a document.
     */
    public static DocumentVersion createNext(
            DocumentVersion previousVersion,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy) {
        return createNext(previousVersion, contentRef, contentHash, Format.XML, createdBy, null, null, List.of());
    }

    public static DocumentVersion createNext(
            DocumentVersion previousVersion,
            ContentRef contentRef,
            ContentHash contentHash,
            Format format,
            String createdBy,
            String parseStatus,
            String messageType,
            List<String> parseErrors) {
        Objects.requireNonNull(previousVersion, "Previous version cannot be null");
        return new DocumentVersion(
                DocumentVersionId.generate(),
                previousVersion.versionNumber() + 1,
                contentRef,
                contentHash,
                format,
                Instant.now(),
                createdBy,
                previousVersion.id(),
                parseStatus,
                messageType,
                parseErrors);
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
        return reconstitute(id, versionNumber, contentRef, contentHash, Format.XML, createdAt, createdBy, previousVersion,
                null, null, List.of());
    }

    public static DocumentVersion reconstitute(
            DocumentVersionId id,
            int versionNumber,
            ContentRef contentRef,
            ContentHash contentHash,
            Format format,
            Instant createdAt,
            String createdBy,
            DocumentVersionId previousVersion,
            String parseStatus,
            String messageType,
            List<String> parseErrors) {
        return new DocumentVersion(id, versionNumber, contentRef, contentHash, format, createdAt, createdBy,
                previousVersion, parseStatus, messageType, parseErrors);
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

    public Format format() {
        return format;
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

    public String parseStatus() {
        return parseStatus;
    }

    public String messageType() {
        return messageType;
    }

    public List<String> parseErrors() {
        return parseErrors;
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
        return "DocumentVersion[id=" + id + ", versionNumber=" + versionNumber + ", contentHash=" + contentHash
                + ", format=" + format + "]";
    }
}
