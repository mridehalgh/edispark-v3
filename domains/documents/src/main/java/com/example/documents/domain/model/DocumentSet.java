package com.example.documents.domain.model;

import com.example.documents.domain.event.DerivativeCreated;
import com.example.documents.domain.event.DocumentAdded;
import com.example.documents.domain.event.DocumentSetCreated;
import com.example.documents.domain.event.DocumentVersionAdded;
import com.example.documents.domain.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The primary aggregate root managing related documents.
 * 
 * <p>A DocumentSet contains one or more related documents with their versions and derivatives.
 * It serves as the consistency boundary for document operations and ensures invariants
 * are maintained across all contained documents.</p>
 * 
 * <p>Domain events are emitted for significant state changes (Requirement 7.6):
 * <ul>
 *   <li>{@link DocumentSetCreated} - when a new DocumentSet is created</li>
 *   <li>{@link DocumentAdded} - when a document is added to the set</li>
 *   <li>{@link DocumentVersionAdded} - when a new version is added to a document</li>
 *   <li>{@link DerivativeCreated} - when a derivative is created</li>
 * </ul>
 * 
 * <p>Requirements: 7.1, 7.2, 7.3, 7.6, 7.7, 7.8</p>
 */
public final class DocumentSet {

    private final DocumentSetId id;
    private final Map<DocumentId, Document> documents;
    private final Instant createdAt;
    private final String createdBy;
    private final Map<String, String> metadata;
    private final List<DomainEvent> domainEvents;

    private DocumentSet(
            DocumentSetId id,
            Map<DocumentId, Document> documents,
            Instant createdAt,
            String createdBy,
            Map<String, String> metadata) {
        this.id = Objects.requireNonNull(id, "DocumentSet ID cannot be null");
        this.documents = new HashMap<>(Objects.requireNonNull(documents, "Documents map cannot be null"));
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "Created by cannot be null");
        this.metadata = new HashMap<>(Objects.requireNonNull(metadata, "Metadata cannot be null"));
        this.domainEvents = new ArrayList<>();
    }

    /**
     * Creates a new empty DocumentSet.
     */
    public static DocumentSet create(String createdBy, Map<String, String> metadata) {
        DocumentSet documentSet = new DocumentSet(
                DocumentSetId.generate(),
                new HashMap<>(),
                Instant.now(),
                createdBy,
                metadata != null ? metadata : Map.of());
        documentSet.registerEvent(DocumentSetCreated.now(documentSet.id, createdBy));
        return documentSet;
    }

    /**
     * Creates a new DocumentSet with an initial document.
     */
    public static DocumentSet createWithDocument(
            DocumentType documentType,
            SchemaVersionRef schemaRef,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy,
            Map<String, String> metadata) {
        DocumentSet documentSet = create(createdBy, metadata);
        documentSet.addDocument(documentType, schemaRef, contentRef, contentHash, createdBy);
        return documentSet;
    }

    /**
     * Reconstitutes a DocumentSet from persistence.
     * 
     * <p>Note: Reconstituted aggregates do not emit events for past state changes.</p>
     */
    public static DocumentSet reconstitute(
            DocumentSetId id,
            Map<DocumentId, Document> documents,
            Instant createdAt,
            String createdBy,
            Map<String, String> metadata) {
        return new DocumentSet(id, documents, createdAt, createdBy, metadata);
    }

    /**
     * Adds a new document to this set.
     * 
     * @return the newly created document
     */
    public Document addDocument(
            DocumentType type,
            SchemaVersionRef schemaRef,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy) {
        Document document = Document.create(type, schemaRef, contentRef, contentHash, createdBy);
        documents.put(document.id(), document);
        
        // Emit DocumentAdded event
        registerEvent(DocumentAdded.now(this.id, document.id(), type, schemaRef));
        
        // Emit DocumentVersionAdded for the initial version
        DocumentVersion firstVersion = document.getCurrentVersion();
        registerEvent(DocumentVersionAdded.now(
                this.id, document.id(), firstVersion.id(), firstVersion.versionNumber(), contentHash));
        
        return document;
    }

    /**
     * Adds a new document with a relation to another document in this set.
     * 
     * @throws IllegalArgumentException if the related document does not exist in this set
     * @return the newly created document
     */
    public Document addDocumentWithRelation(
            DocumentType type,
            SchemaVersionRef schemaRef,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy,
            DocumentId relatedDocumentId) {
        validateRelatedDocumentExists(relatedDocumentId);
        Document document = Document.createWithRelation(
                type, schemaRef, contentRef, contentHash, createdBy, relatedDocumentId);
        documents.put(document.id(), document);
        
        // Emit DocumentAdded event
        registerEvent(DocumentAdded.now(this.id, document.id(), type, schemaRef));
        
        // Emit DocumentVersionAdded for the initial version
        DocumentVersion firstVersion = document.getCurrentVersion();
        registerEvent(DocumentVersionAdded.now(
                this.id, document.id(), firstVersion.id(), firstVersion.versionNumber(), contentHash));
        
        return document;
    }

    /**
     * Adds a new version to an existing document in this set.
     * 
     * @throws IllegalArgumentException if the document does not exist in this set
     * @return the newly created version
     */
    public DocumentVersion addVersion(
            DocumentId documentId,
            ContentRef contentRef,
            ContentHash contentHash,
            String createdBy) {
        Document document = getDocumentOrThrow(documentId);
        DocumentVersion newVersion = document.addVersion(contentRef, contentHash, createdBy);
        
        // Emit DocumentVersionAdded event
        registerEvent(DocumentVersionAdded.now(
                this.id, documentId, newVersion.id(), newVersion.versionNumber(), contentHash));
        
        return newVersion;
    }

    /**
     * Creates a derivative for a document version in this set.
     * 
     * @throws IllegalArgumentException if the document does not exist in this set
     * @throws DuplicateDerivativeException if a derivative with the same source and format exists
     * @return the newly created derivative
     */
    public Derivative createDerivative(
            DocumentId documentId,
            DocumentVersionId sourceVersionId,
            Format targetFormat,
            ContentRef contentRef,
            ContentHash contentHash,
            TransformationMethod method) {
        Document document = getDocumentOrThrow(documentId);
        Derivative derivative = document.addDerivative(sourceVersionId, targetFormat, contentRef, contentHash, method);
        
        // Emit DerivativeCreated event
        registerEvent(DerivativeCreated.now(
                this.id, documentId, derivative.id(), sourceVersionId, targetFormat));
        
        return derivative;
    }

    /**
     * Returns a document by its ID.
     */
    public Optional<Document> getDocument(DocumentId documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

    /**
     * Returns all documents of a specific type.
     */
    public List<Document> getDocumentsByType(DocumentType type) {
        return documents.values().stream()
                .filter(doc -> doc.type().equals(type))
                .toList();
    }

    /**
     * Returns all documents in this set.
     */
    public List<Document> getAllDocuments() {
        return List.copyOf(documents.values());
    }

    /**
     * Returns the number of documents in this set.
     */
    public int documentCount() {
        return documents.size();
    }

    /**
     * Checks if this set contains a document with the given ID.
     */
    public boolean containsDocument(DocumentId documentId) {
        return documents.containsKey(documentId);
    }

    /**
     * Returns all domain events that have been emitted since the last clear.
     * 
     * @return an unmodifiable list of domain events
     */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears all collected domain events.
     * 
     * <p>This should be called after events have been published to avoid re-publishing.</p>
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public DocumentSetId id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public Map<String, String> metadata() {
        return Collections.unmodifiableMap(metadata);
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    private Document getDocumentOrThrow(DocumentId documentId) {
        return getDocument(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document " + documentId + " not found in this DocumentSet"));
    }

    private void validateRelatedDocumentExists(DocumentId relatedDocumentId) {
        if (relatedDocumentId != null && !containsDocument(relatedDocumentId)) {
            throw new IllegalArgumentException(
                    "Related document " + relatedDocumentId + " does not exist in this DocumentSet");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentSet that = (DocumentSet) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DocumentSet[id=" + id + ", documents=" + documents.size() + 
               ", createdAt=" + createdAt + ", createdBy=" + createdBy + "]";
    }
}
