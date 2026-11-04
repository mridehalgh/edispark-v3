package com.edispark.documents.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A business document within a DocumentSet.
 * 
 * <p>A Document maintains its version history and associated derivatives. The document's
 * identity remains stable across all versions and derivatives.</p>
 * 
 * <p>Requirements: 1.1, 1.3, 1.4, 2.4, 2.5, 2.6, 5.4, 5.5, 7.5</p>
 */
public final class Document {

    private final DocumentId id;
    private final DocumentType type;
    private final SchemaVersionRef schemaRef;
    private final List<DocumentVersion> versions;
    private final List<Derivative> derivatives;
    private final List<DocumentRelation> relations;
    private DocumentLifecycleStatus lifecycleStatus;

    private Document(
            DocumentId id,
            DocumentType type,
            SchemaVersionRef schemaRef,
            List<DocumentVersion> versions,
            List<Derivative> derivatives,
            List<DocumentRelation> relations,
            DocumentLifecycleStatus lifecycleStatus) {
        this.id = Objects.requireNonNull(id, "Document ID cannot be null");
        this.type = Objects.requireNonNull(type, "Document type cannot be null");
        this.schemaRef = Objects.requireNonNull(schemaRef, "Schema reference cannot be null");
        this.versions = new ArrayList<>(Objects.requireNonNull(versions, "Versions list cannot be null"));
        this.derivatives = new ArrayList<>(Objects.requireNonNull(derivatives, "Derivatives list cannot be null"));
        this.relations = new ArrayList<>(Objects.requireNonNull(relations, "Relations list cannot be null"));
        this.lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "Lifecycle status cannot be null");
    }

    /**
     * Creates a new Document with an initial version.
     */
    public static Document create(
            DocumentType type,
            SchemaVersionRef schemaRef,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy) {
        return create(type, schemaRef, contentRef, contentHash, createdBy, Format.XML, null, null, List.of());
    }

    public static Document create(
            DocumentType type,
            SchemaVersionRef schemaRef,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy,
            Format format,
            String parseStatus,
            String messageType,
            List<String> parseErrors) {
        DocumentVersion firstVersion = DocumentVersion.createFirst(contentRef, contentHash, format, createdBy,
                parseStatus, messageType, parseErrors);
        return new Document(
                DocumentId.generate(),
                type,
                schemaRef,
                List.of(firstVersion),
                List.of(),
                List.of(),
                DocumentLifecycleStatus.FINAL);
    }

    /**
     * Creates a new Document with an initial version and a related document reference.
     */
    public static Document createWithRelation(
            DocumentType type,
            SchemaVersionRef schemaRef,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy,
            DocumentId relatedDocumentId) {
        return createWithRelation(type, schemaRef, contentRef, contentHash, createdBy, relatedDocumentId, Format.XML,
                null, null, List.of());
    }

    public static Document createWithRelation(
            DocumentType type,
            SchemaVersionRef schemaRef,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy,
            DocumentId relatedDocumentId,
            Format format,
            String parseStatus,
            String messageType,
            List<String> parseErrors) {
        DocumentVersion firstVersion = DocumentVersion.createFirst(contentRef, contentHash, format, createdBy,
                parseStatus, messageType, parseErrors);
        return new Document(
                DocumentId.generate(),
                type,
                schemaRef,
                List.of(firstVersion),
                List.of(),
                relatedDocumentId == null ? List.of() : List.of(new DocumentRelation(
                        DocumentRelationType.RELATED_TO, relatedDocumentId)),
                DocumentLifecycleStatus.FINAL);
    }

    /**
     * Reconstitutes a Document from persistence.
     */
    public static Document reconstitute(
            DocumentId id,
            DocumentType type,
            SchemaVersionRef schemaRef,
            List<DocumentVersion> versions,
            List<Derivative> derivatives,
            DocumentId relatedDocumentId) {
        List<DocumentRelation> relations = relatedDocumentId == null
                ? List.of()
                : List.of(new DocumentRelation(DocumentRelationType.RELATED_TO, relatedDocumentId));
        return new Document(id, type, schemaRef, versions, derivatives, relations, DocumentLifecycleStatus.FINAL);
    }

    public static Document reconstitute(
            DocumentId id,
            DocumentType type,
            SchemaVersionRef schemaRef,
            List<DocumentVersion> versions,
            List<Derivative> derivatives,
            List<DocumentRelation> relations,
            DocumentLifecycleStatus lifecycleStatus) {
        return new Document(id, type, schemaRef, versions, derivatives, relations, lifecycleStatus);
    }

    public void markDraft() {
        requireStatus(DocumentLifecycleStatus.FINAL, "Only final documents can be reopened as a draft");
        lifecycleStatus = DocumentLifecycleStatus.DRAFT;
    }

    public void finalizeDocument() {
        requireStatus(DocumentLifecycleStatus.DRAFT, "Only draft documents can be finalized");
        lifecycleStatus = DocumentLifecycleStatus.FINAL;
    }

    public void cancel() {
        if (lifecycleStatus == DocumentLifecycleStatus.CANCELLED) {
            throw new IllegalStateException("Document is already cancelled");
        }
        lifecycleStatus = DocumentLifecycleStatus.CANCELLED;
    }

    public void addRelation(DocumentRelation relation) {
        Objects.requireNonNull(relation, "Relation cannot be null");
        if (!relations.contains(relation)) {
            relations.add(relation);
        }
    }

    private void requireStatus(DocumentLifecycleStatus required, String message) {
        if (lifecycleStatus != required) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * Adds a new version to this document.
     * 
     * @throws InvalidVersionSequenceException if the version sequence would be broken
     */
    public DocumentVersion addVersion(ContentRef contentRef, ContentHash contentHash, String createdBy) {
        return addVersion(contentRef, contentHash, createdBy, Format.XML, null, null, List.of());
    }

    public DocumentVersion addVersion(
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy,
            Format format,
            String parseStatus,
            String messageType,
            List<String> parseErrors) {
        if (lifecycleStatus == DocumentLifecycleStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled documents cannot be changed");
        }
        if (versions.isEmpty()) {
            throw new IllegalStateException("Document must have at least one version");
        }
        
        DocumentVersion currentVersion = getCurrentVersion();
        DocumentVersion newVersion = DocumentVersion.createNext(currentVersion, contentRef, contentHash, format,
                createdBy, parseStatus, messageType, parseErrors);
        versions.add(newVersion);
        return newVersion;
    }

    /**
     * Adds a derivative to this document.
     * 
     * @throws DuplicateDerivativeException if a derivative with the same source version and target format exists
     */
    public Derivative addDerivative(
            DocumentVersionId sourceVersionId,
            Format targetFormat,
            ContentRef contentRef,
            ContentHash contentHash,
            TransformationMethod method) {
        return addDerivative(sourceVersionId, targetFormat, contentRef, contentHash, method, "default");
    }

    public Derivative addDerivative(
            DocumentVersionId sourceVersionId,
            Format targetFormat,
            ContentRef contentRef,
            ContentHash contentHash,
            TransformationMethod method,
            String variantKey) {
        
        // Validate source version exists
        if (getVersionById(sourceVersionId).isEmpty()) {
            throw new IllegalArgumentException("Source version " + sourceVersionId + " not found in this document");
        }
        
        // Check for duplicate derivative
        if (hasDerivative(sourceVersionId, targetFormat, variantKey)) {
            throw new DuplicateDerivativeException(sourceVersionId, targetFormat);
        }
        
        Derivative derivative = Derivative.create(sourceVersionId, targetFormat, contentRef, contentHash, method, variantKey);
        derivatives.add(derivative);
        return derivative;
    }

    /**
     * Returns the current (latest) version of this document.
     */
    public DocumentVersion getCurrentVersion() {
        if (versions.isEmpty()) {
            throw new IllegalStateException("Document has no versions");
        }
        return versions.stream()
                .max((v1, v2) -> Integer.compare(v1.versionNumber(), v2.versionNumber()))
                .orElseThrow();
    }

    /**
     * Returns a specific version by version number.
     */
    public Optional<DocumentVersion> getVersion(int versionNumber) {
        return versions.stream()
                .filter(v -> v.versionNumber() == versionNumber)
                .findFirst();
    }

    /**
     * Returns a specific version by ID.
     */
    public Optional<DocumentVersion> getVersionById(DocumentVersionId versionId) {
        return versions.stream()
                .filter(v -> v.id().equals(versionId))
                .findFirst();
    }

    /**
     * Returns all derivatives for a specific format.
     */
    public List<Derivative> getDerivativesByFormat(Format format) {
        return derivatives.stream()
                .filter(d -> d.targetFormat().equals(format))
                .toList();
    }

    /**
     * Returns the derivative for a specific source version and target format, if it exists.
     */
    public Optional<Derivative> getDerivative(DocumentVersionId sourceVersionId, Format targetFormat) {
        return derivatives.stream()
                .filter(d -> d.matches(sourceVersionId, targetFormat))
                .findFirst();
    }

    /**
     * Returns a derivative by its identifier, if it exists.
     */
    public Optional<Derivative> getDerivative(DerivativeId derivativeId) {
        return derivatives.stream()
                .filter(derivative -> derivative.id().equals(derivativeId))
                .findFirst();
    }

    /**
     * Checks if a derivative exists for the given source version and target format.
     */
    public boolean hasDerivative(DocumentVersionId sourceVersionId, Format targetFormat) {
        return derivatives.stream()
                .anyMatch(d -> d.matches(sourceVersionId, targetFormat));
    }

    public boolean hasDerivative(DocumentVersionId sourceVersionId, Format targetFormat, String variantKey) {
        return derivatives.stream().anyMatch(d -> d.matches(sourceVersionId, targetFormat, variantKey));
    }

    /**
     * Returns the total number of versions.
     */
    public int versionCount() {
        return versions.size();
    }

    public DocumentId id() {
        return id;
    }

    public DocumentType type() {
        return type;
    }

    public SchemaVersionRef schemaRef() {
        return schemaRef;
    }

    public List<DocumentVersion> versions() {
        return Collections.unmodifiableList(versions);
    }

    public List<Derivative> derivatives() {
        return Collections.unmodifiableList(derivatives);
    }

    public Optional<DocumentId> relatedDocumentId() {
        return relations.stream().findFirst().map(DocumentRelation::targetDocumentId);
    }

    public List<DocumentRelation> relations() {
        return Collections.unmodifiableList(relations);
    }

    public DocumentLifecycleStatus lifecycleStatus() {
        return lifecycleStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Objects.equals(id, document.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Document[id=" + id + ", type=" + type + ", versions=" + versions.size() + 
               ", derivatives=" + derivatives.size() + "]";
    }
}
