package com.example.documents.infrastructure.persistence;

import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.VersionIdentifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DynamoDbSchemaRepository using DynamoDB Local.
 * 
 * <p>Requirements: 3.6</p>
 */
class DynamoDbSchemaRepositoryIntegrationTest {

    private static DynamoDbLocalTestSupport dynamoDbLocal;
    private static DynamoDbTableConfig tableConfig;
    private DynamoDbSchemaRepository schemaRepository;
    private DynamoDbDocumentSetRepository documentSetRepository;

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
        schemaRepository = new DynamoDbSchemaRepository(dynamoDbLocal.client(), tableConfig.tableName());
        documentSetRepository = new DynamoDbDocumentSetRepository(dynamoDbLocal.client(), tableConfig.tableName());
    }

    @Test
    void shouldSaveAndFindSchemaById() {
        // Given
        Schema schema = Schema.create("UBL Invoice Schema", SchemaFormat.JSON_SCHEMA);
        ContentHash definitionHash = ContentHash.sha256("schemadefinition123");
        schema.addVersion(VersionIdentifier.of("2.1.0"), ContentRef.of(definitionHash));
        
        // When
        schemaRepository.save(schema);
        Optional<Schema> found = schemaRepository.findById(schema.id());
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(schema.id());
        assertThat(found.get().name()).isEqualTo("UBL Invoice Schema");
        assertThat(found.get().format()).isEqualTo(SchemaFormat.JSON_SCHEMA);
        assertThat(found.get().versionCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenSchemaNotFound() {
        // When
        Optional<Schema> found = schemaRepository.findById(SchemaId.generate());
        
        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldSaveSchemaWithMultipleVersions() {
        // Given
        Schema schema = Schema.create("Test Schema", SchemaFormat.XSD);
        schema.addVersion(VersionIdentifier.of("1.0.0"), ContentRef.of(ContentHash.sha256("v1")));
        schema.addVersion(VersionIdentifier.of("1.1.0"), ContentRef.of(ContentHash.sha256("v11")));
        schema.addVersion(VersionIdentifier.of("2.0.0"), ContentRef.of(ContentHash.sha256("v2")));
        
        // When
        schemaRepository.save(schema);
        Optional<Schema> found = schemaRepository.findById(schema.id());
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().versionCount()).isEqualTo(3);
        assertThat(found.get().getVersion(VersionIdentifier.of("1.0.0"))).isPresent();
        assertThat(found.get().getVersion(VersionIdentifier.of("1.1.0"))).isPresent();
        assertThat(found.get().getVersion(VersionIdentifier.of("2.0.0"))).isPresent();
    }

    @Test
    void shouldDetectReferencedSchemaVersion() {
        // Given - create a schema with a version
        Schema schema = Schema.create("Referenced Schema", SchemaFormat.JSON_SCHEMA);
        schema.addVersion(VersionIdentifier.of("1.0.0"), ContentRef.of(ContentHash.sha256("def")));
        schemaRepository.save(schema);
        
        // Create a document set that references this schema version
        SchemaVersionRef schemaRef = SchemaVersionRef.of(schema.id(), VersionIdentifier.of("1.0.0"));
        ContentHash docHash = ContentHash.sha256("dochash");
        DocumentSet documentSet = DocumentSet.createWithDocument(
                DocumentType.INVOICE, schemaRef, ContentRef.of(docHash), docHash, "user", Map.of());
        documentSetRepository.save(documentSet);
        
        // When
        boolean isReferenced = schemaRepository.isVersionReferenced(schemaRef);
        
        // Then
        assertThat(isReferenced).isTrue();
    }

    @Test
    void shouldNotDetectUnreferencedSchemaVersion() {
        // Given - create a schema with a version but no documents reference it
        Schema schema = Schema.create("Unreferenced Schema", SchemaFormat.XSD);
        schema.addVersion(VersionIdentifier.of("1.0.0"), ContentRef.of(ContentHash.sha256("def")));
        schemaRepository.save(schema);
        
        SchemaVersionRef schemaRef = SchemaVersionRef.of(schema.id(), VersionIdentifier.of("1.0.0"));
        
        // When
        boolean isReferenced = schemaRepository.isVersionReferenced(schemaRef);
        
        // Then
        assertThat(isReferenced).isFalse();
    }

    @Test
    void shouldUpdateExistingSchema() {
        // Given
        Schema schema = Schema.create("Evolving Schema", SchemaFormat.JSON_SCHEMA);
        schema.addVersion(VersionIdentifier.of("1.0.0"), ContentRef.of(ContentHash.sha256("v1")));
        schemaRepository.save(schema);
        
        // When - add another version and save again
        schema.addVersion(VersionIdentifier.of("2.0.0"), ContentRef.of(ContentHash.sha256("v2")));
        schemaRepository.save(schema);
        
        // Then
        Optional<Schema> found = schemaRepository.findById(schema.id());
        assertThat(found).isPresent();
        assertThat(found.get().versionCount()).isEqualTo(2);
    }

    @Test
    void shouldPreserveSchemaVersionMetadata() {
        // Given
        Schema schema = Schema.create("Metadata Test Schema", SchemaFormat.RELAXNG);
        ContentHash hash = ContentHash.sha256("metadatatest");
        schema.addVersion(VersionIdentifier.of("3.2.1"), ContentRef.of(hash));
        
        // When
        schemaRepository.save(schema);
        Optional<Schema> found = schemaRepository.findById(schema.id());
        
        // Then
        assertThat(found).isPresent();
        var version = found.get().getVersion(VersionIdentifier.of("3.2.1"));
        assertThat(version).isPresent();
        assertThat(version.get().definitionRef().hash()).isEqualTo(hash);
        assertThat(version.get().isDeprecated()).isFalse();
        assertThat(version.get().createdAt()).isNotNull();
    }
}
