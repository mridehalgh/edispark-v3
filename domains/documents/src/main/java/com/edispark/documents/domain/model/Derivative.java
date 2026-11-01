package com.edispark.documents.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A transformed representation of a document in a different format.
 * 
 * <p>Derivatives are created by transforming a source document version into a target format.
 * Each derivative maintains a reference to its source version and records the transformation
 * method used.</p>
 * 
 * <p>Requirements: 5.1, 5.2, 5.3, 5.7</p>
 */
public final class Derivative {

    private final DerivativeId id;
    private final DocumentVersionId sourceVersionId;
    private final Format targetFormat;
    private final ContentRef contentRef;
    private final ContentHash contentHash;
    private final TransformationMethod method;
    private final String variantKey;
    private final Instant createdAt;

    private Derivative(
            DerivativeId id,
            DocumentVersionId sourceVersionId,
            Format targetFormat,
            ContentRef contentRef,
            ContentHash contentHash,
            TransformationMethod method,
            String variantKey,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Derivative ID cannot be null");
        this.sourceVersionId = Objects.requireNonNull(sourceVersionId, "Source version ID cannot be null");
        this.targetFormat = Objects.requireNonNull(targetFormat, "Target format cannot be null");
        this.contentRef = Objects.requireNonNull(contentRef, "Content reference cannot be null");
        this.contentHash = Objects.requireNonNull(contentHash, "Content hash cannot be null");
        this.method = Objects.requireNonNull(method, "Transformation method cannot be null");
        this.variantKey = requireVariantKey(variantKey);
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
    }

    /**
     * Creates a new derivative from a source document version.
     */
    public static Derivative create(
            DocumentVersionId sourceVersionId,
            Format targetFormat,
            ContentRef contentRef,
            ContentHash contentHash,
            TransformationMethod method) {
        return create(sourceVersionId, targetFormat, contentRef, contentHash, method, "default");
    }

    public static Derivative create(
            DocumentVersionId sourceVersionId,
            Format targetFormat,
            ContentRef contentRef,
            ContentHash contentHash,
            TransformationMethod method,
            String variantKey) {
        return new Derivative(
                DerivativeId.generate(),
                sourceVersionId,
                targetFormat,
                contentRef,
                contentHash,
                method,
                variantKey,
                Instant.now());
    }

    /**
     * Reconstitutes a Derivative from persistence.
     */
    public static Derivative reconstitute(
            DerivativeId id,
            DocumentVersionId sourceVersionId,
            Format targetFormat,
            ContentRef contentRef,
            ContentHash contentHash,
            TransformationMethod method,
            Instant createdAt) {
        return reconstitute(id, sourceVersionId, targetFormat, contentRef, contentHash, method, "default", createdAt);
    }

    public static Derivative reconstitute(
            DerivativeId id,
            DocumentVersionId sourceVersionId,
            Format targetFormat,
            ContentRef contentRef,
            ContentHash contentHash,
            TransformationMethod method,
            String variantKey,
            Instant createdAt) {
        return new Derivative(id, sourceVersionId, targetFormat, contentRef, contentHash, method, variantKey, createdAt);
    }

    public DerivativeId id() {
        return id;
    }

    public DocumentVersionId sourceVersionId() {
        return sourceVersionId;
    }

    public Format targetFormat() {
        return targetFormat;
    }

    public ContentRef contentRef() {
        return contentRef;
    }

    public ContentHash contentHash() {
        return contentHash;
    }

    public TransformationMethod method() {
        return method;
    }

    public String variantKey() { return variantKey; }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Checks if this derivative matches the given source version and target format.
     */
    public boolean matches(DocumentVersionId sourceVersionId, Format targetFormat) {
        return this.sourceVersionId.equals(sourceVersionId) && this.targetFormat.equals(targetFormat);
    }

    public boolean matches(DocumentVersionId sourceVersionId, Format targetFormat, String variantKey) {
        return matches(sourceVersionId, targetFormat) && this.variantKey.equals(variantKey);
    }

    private static String requireVariantKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException("Derivative variant key must contain 1-200 characters");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Derivative that = (Derivative) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Derivative[id=" + id + ", sourceVersionId=" + sourceVersionId + 
               ", targetFormat=" + targetFormat + ", method=" + method + "]";
    }
}
