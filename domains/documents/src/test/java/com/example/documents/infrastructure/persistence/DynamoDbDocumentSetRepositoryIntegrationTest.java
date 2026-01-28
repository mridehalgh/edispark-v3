package com.example.documents.infrastructure.persistence;

import com.example.common.pagination.Page;
import com.example.common.pagination.PaginatedResult;
import com.example.documents.application.handler.InvalidPaginationTokenException;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.TransformationMethod;
import com.example.documents.domain.model.VersionIdentifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DynamoDbDocumentSetRepository using DynamoDB Local.
 * 
 * <p>Requirements: 10.1, 10.2, 10.5</p>
 */
class DynamoDbDocumentSetRepositoryIntegrationTest {

    private static DynamoDbLocalTestSupport dynamoDbLocal;
    private static DynamoDbTableConfig tableConfig;
    private DynamoDbDocumentSetRepository repository;

    @BeforeAll
    static void startDynamoDbLocal() throws Exception {
        dynamoDbLocal = new DynamoDbLocalTestSupport();
        dynamoDbLocal.start();
        tableConfig = new DynamoDbTableConfig("test-documents");
    }

    @AfterAll
    static void stopDynamoDbLocal() throws Exception {
        if (dynamoDbLocal != null) {
            dynamoDbLocal.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // Recreate table for each test
        tableConfig.deleteTableIfExists(dynamoDbLocal.client());
        tableConfig.createTableIfNotExists(dynamoDbLocal.client());
        repository = new DynamoDbDocumentSetRepository(dynamoDbLocal.client(), tableConfig.tableName());
    }

    @Test
    void shouldSaveAndFindDocumentSetById() {
        // Given
        ContentHash contentHash = ContentHash.sha256("abc123def456");
        ContentRef contentRef = ContentRef.of(contentHash);
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("2.1.0"));
        
        DocumentSet documentSet = DocumentSet.createWithDocument(
                DocumentType.INVOICE,
                schemaRef,
                contentRef,
                contentHash,
                "test-user",
                Map.of("project", "test"));
        
        // When
        repository.save(documentSet);
        Optional<DocumentSet> found = repository.findById(documentSet.id());
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(documentSet.id());
        assertThat(found.get().createdBy()).isEqualTo("test-user");
        assertThat(found.get().metadata()).containsEntry("project", "test");
        assertThat(found.get().documentCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenDocumentSetNotFound() {
        // When
        Optional<DocumentSet> found = repository.findById(DocumentSetId.generate());
        
        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindAllDocumentSets() {
        // Given
        ContentHash contentHash1 = ContentHash.sha256("hash1");
        ContentHash contentHash2 = ContentHash.sha256("hash2");
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        DocumentSet docSet1 = DocumentSet.createWithDocument(
                DocumentType.INVOICE, schemaRef, ContentRef.of(contentHash1), contentHash1, "user1", Map.of());
        DocumentSet docSet2 = DocumentSet.createWithDocument(
                DocumentType.ORDER, schemaRef, ContentRef.of(contentHash2), contentHash2, "user2", Map.of());
        
        repository.save(docSet1);
        repository.save(docSet2);
        
        // When
        List<DocumentSet> all = repository.findAll();
        
        // Then
        assertThat(all).hasSize(2);
        assertThat(all).extracting(DocumentSet::id)
                .containsExactlyInAnyOrder(docSet1.id(), docSet2.id());
    }

    @Test
    void shouldDeleteDocumentSet() {
        // Given
        ContentHash contentHash = ContentHash.sha256("todelete");
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        DocumentSet documentSet = DocumentSet.createWithDocument(
                DocumentType.INVOICE, schemaRef, ContentRef.of(contentHash), contentHash, "user", Map.of());
        repository.save(documentSet);
        
        // When
        repository.delete(documentSet.id());
        
        // Then
        assertThat(repository.findById(documentSet.id())).isEmpty();
    }

    @Test
    void shouldFindByContentHash() {
        // Given
        ContentHash targetHash = ContentHash.sha256("findme123");
        ContentHash otherHash = ContentHash.sha256("other456");
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        DocumentSet docSetWithTarget = DocumentSet.createWithDocument(
                DocumentType.INVOICE, schemaRef, ContentRef.of(targetHash), targetHash, "user1", Map.of());
        DocumentSet docSetWithOther = DocumentSet.createWithDocument(
                DocumentType.ORDER, schemaRef, ContentRef.of(otherHash), otherHash, "user2", Map.of());
        
        repository.save(docSetWithTarget);
        repository.save(docSetWithOther);
        
        // When
        List<DocumentSet> found = repository.findByContentHash(targetHash);
        
        // Then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).id()).isEqualTo(docSetWithTarget.id());
    }

    @Test
    void shouldSaveDocumentSetWithMultipleDocuments() {
        // Given
        ContentHash hash1 = ContentHash.sha256("doc1hash");
        ContentHash hash2 = ContentHash.sha256("doc2hash");
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        DocumentSet documentSet = DocumentSet.createWithDocument(
                DocumentType.INVOICE, schemaRef, ContentRef.of(hash1), hash1, "user", Map.of());
        documentSet.addDocument(DocumentType.CREDIT_NOTE, schemaRef, ContentRef.of(hash2), hash2, "user");
        
        // When
        repository.save(documentSet);
        Optional<DocumentSet> found = repository.findById(documentSet.id());
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().documentCount()).isEqualTo(2);
        assertThat(found.get().getDocumentsByType(DocumentType.INVOICE)).hasSize(1);
        assertThat(found.get().getDocumentsByType(DocumentType.CREDIT_NOTE)).hasSize(1);
    }

    @Test
    void shouldSaveDocumentSetWithVersions() {
        // Given
        ContentHash hash1 = ContentHash.sha256("version1");
        ContentHash hash2 = ContentHash.sha256("version2");
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        DocumentSet documentSet = DocumentSet.createWithDocument(
                DocumentType.INVOICE, schemaRef, ContentRef.of(hash1), hash1, "user", Map.of());
        var document = documentSet.getAllDocuments().get(0);
        documentSet.addVersion(document.id(), ContentRef.of(hash2), hash2, "user");
        
        // When
        repository.save(documentSet);
        Optional<DocumentSet> found = repository.findById(documentSet.id());
        
        // Then
        assertThat(found).isPresent();
        var foundDoc = found.get().getAllDocuments().get(0);
        assertThat(foundDoc.versionCount()).isEqualTo(2);
        assertThat(foundDoc.getCurrentVersion().versionNumber()).isEqualTo(2);
    }

    @Test
    void shouldSaveDocumentSetWithDerivatives() {
        // Given
        ContentHash docHash = ContentHash.sha256("document");
        ContentHash derivativeHash = ContentHash.sha256("derivative");
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        DocumentSet documentSet = DocumentSet.createWithDocument(
                DocumentType.INVOICE, schemaRef, ContentRef.of(docHash), docHash, "user", Map.of());
        var document = documentSet.getAllDocuments().get(0);
        var version = document.getCurrentVersion();
        documentSet.createDerivative(
                document.id(), version.id(), Format.JSON, ContentRef.of(derivativeHash), 
                derivativeHash, TransformationMethod.PROGRAMMATIC);
        
        // When
        repository.save(documentSet);
        Optional<DocumentSet> found = repository.findById(documentSet.id());
        
        // Then
        assertThat(found).isPresent();
        var foundDoc = found.get().getAllDocuments().get(0);
        assertThat(foundDoc.derivatives()).hasSize(1);
        assertThat(foundDoc.derivatives().get(0).targetFormat()).isEqualTo(Format.JSON);
    }

    @Test
    void shouldUpdateExistingDocumentSet() {
        // Given
        ContentHash hash1 = ContentHash.sha256("initial");
        ContentHash hash2 = ContentHash.sha256("added");
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        DocumentSet documentSet = DocumentSet.createWithDocument(
                DocumentType.INVOICE, schemaRef, ContentRef.of(hash1), hash1, "user", Map.of());
        repository.save(documentSet);
        
        // When - add another document and save again
        documentSet.addDocument(DocumentType.ORDER, schemaRef, ContentRef.of(hash2), hash2, "user");
        repository.save(documentSet);
        
        // Then
        Optional<DocumentSet> found = repository.findById(documentSet.id());
        assertThat(found).isPresent();
        assertThat(found.get().documentCount()).isEqualTo(2);
    }

    // Pagination Tests

    @Test
    void shouldPaginateThroughMultiplePages() {
        // Given - seed 30 document sets
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        List<DocumentSetId> seededIds = new ArrayList<>();
        
        for (int i = 0; i < 30; i++) {
            ContentHash hash = ContentHash.sha256("hash-" + i);
            DocumentSet docSet = DocumentSet.createWithDocument(
                    DocumentType.INVOICE, schemaRef, ContentRef.of(hash), hash, "user", Map.of());
            repository.save(docSet);
            seededIds.add(docSet.id());
        }
        
        // When - paginate with page size of 10
        Page page1 = Page.first(10);
        PaginatedResult<DocumentSet> result1 = repository.findAll(page1);
        
        assertThat(result1.items()).hasSize(10);
        assertThat(result1.hasMore()).isTrue();
        
        Page page2 = Page.next(10, result1.continuationToken().get());
        PaginatedResult<DocumentSet> result2 = repository.findAll(page2);
        
        assertThat(result2.items()).hasSize(10);
        assertThat(result2.hasMore()).isTrue();
        
        Page page3 = Page.next(10, result2.continuationToken().get());
        PaginatedResult<DocumentSet> result3 = repository.findAll(page3);
        
        assertThat(result3.items()).hasSize(10);
        // Note: DynamoDB may return a continuation token even on the last page
        // The important thing is that all items are retrieved
        
        // Verify all items retrieved
        Set<DocumentSetId> retrievedIds = new HashSet<>();
        retrievedIds.addAll(result1.items().stream().map(DocumentSet::id).toList());
        retrievedIds.addAll(result2.items().stream().map(DocumentSet::id).toList());
        retrievedIds.addAll(result3.items().stream().map(DocumentSet::id).toList());
        
        assertThat(retrievedIds).containsExactlyInAnyOrderElementsOf(seededIds);
        
        // If there's a continuation token, verify the next page is empty
        if (result3.hasMore()) {
            Page page4 = Page.next(10, result3.continuationToken().get());
            PaginatedResult<DocumentSet> result4 = repository.findAll(page4);
            assertThat(result4.items()).isEmpty();
        }
    }

    @Test
    void shouldReturnEmptyResultWhenNoDocumentSets() {
        // When
        Page page = Page.first(20);
        PaginatedResult<DocumentSet> result = repository.findAll(page);
        
        // Then
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void shouldReturnLastPageWithoutToken() {
        // Given - seed 15 document sets
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        for (int i = 0; i < 15; i++) {
            ContentHash hash = ContentHash.sha256("hash-" + i);
            DocumentSet docSet = DocumentSet.createWithDocument(
                    DocumentType.INVOICE, schemaRef, ContentRef.of(hash), hash, "user", Map.of());
            repository.save(docSet);
        }
        
        // When - request page size of 20 (more than available)
        Page page = Page.first(20);
        PaginatedResult<DocumentSet> result = repository.findAll(page);
        
        // Then
        assertThat(result.items()).hasSize(15);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.continuationToken()).isEmpty();
    }

    @Test
    void shouldHandleInvalidPaginationToken() {
        // When/Then
        assertThatThrownBy(() -> {
            Page page = Page.next(10, "invalid-token-xyz");
            repository.findAll(page);
        }).isInstanceOf(InvalidPaginationTokenException.class);
    }

    @Test
    void shouldPaginateWithDifferentPageSizes() {
        // Given - seed 25 document sets
        SchemaVersionRef schemaRef = SchemaVersionRef.of(SchemaId.generate(), VersionIdentifier.of("1.0.0"));
        
        for (int i = 0; i < 25; i++) {
            ContentHash hash = ContentHash.sha256("hash-" + i);
            DocumentSet docSet = DocumentSet.createWithDocument(
                    DocumentType.INVOICE, schemaRef, ContentRef.of(hash), hash, "user", Map.of());
            repository.save(docSet);
        }
        
        // When - first page with size 5
        Page page1 = Page.first(5);
        PaginatedResult<DocumentSet> result1 = repository.findAll(page1);
        
        assertThat(result1.items()).hasSize(5);
        assertThat(result1.hasMore()).isTrue();
        
        // Then - second page with size 20
        Page page2 = Page.next(20, result1.continuationToken().get());
        PaginatedResult<DocumentSet> result2 = repository.findAll(page2);
        
        assertThat(result2.items()).hasSize(20);
        // Note: DynamoDB may return a continuation token even on the last page
        // Verify total items retrieved equals seeded count
        assertThat(result1.items().size() + result2.items().size()).isEqualTo(25);
    }

}
