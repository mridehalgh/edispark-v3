package com.example.documents.infrastructure.persistence;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
}
