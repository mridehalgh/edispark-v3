package com.example.documents.domain.model;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for DocumentSet multi-type support.
 * 
 * <p><b>Property 14: DocumentSet Multi-Type Support</b></p>
 * <p>A DocumentSet SHALL support documents of different types, provide access to all 
 * documents and their current versions, and allow adding new documents to an existing set.</p>
 * 
 * <p><b>Validates: Requirements 7.1, 7.2, 7.3, 7.7</b></p>
 */
class DocumentSetMultiTypeSupportPropertyTest {

    @Provide
    Arbitrary<SchemaVersionRef> schemaVersionRefs() {
        return Arbitraries.of(
                SchemaVersionRef.of(
                        new SchemaId(UUID.randomUUID()),
                        new VersionIdentifier("1.0.0")),
                SchemaVersionRef.of(
                        new SchemaId(UUID.randomUUID()),
                        new VersionIdentifier("2.1.0")));
    }

    @Provide
    Arbitrary<DocumentType> documentTypes() {
        return Arbitraries.of(DocumentType.values());
    }

    @Provide
    Arbitrary<List<DocumentType>> documentTypeLists() {
        return Arbitraries.of(DocumentType.values())
                .list()
                .ofMinSize(1)
                .ofMaxSize(5);
    }

    /**
     * Property 14: DocumentSet Multi-Type Support
     * 
     * <p>Requirement 7.1: THE DocumentSet SHALL contain one or more related documents 
     * with their versions and derivatives.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 14: DocumentSet contains documents with versions")
    void documentSetContainsDocumentsWithVersions(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 1, max = 5) int documentCount,
            @ForAll @IntRange(min = 1, max = 3) int versionsPerDocument) {
        
        DocumentSet documentSet = DocumentSet.create("user1", Map.of("project", "test"));

        // Add documents with versions
        for (int d = 0; d < documentCount; d++) {
            Content initialContent = Content.of(("doc" + d).getBytes(), Format.XML);
            Document doc = documentSet.addDocument(
                    DocumentType.values()[d % DocumentType.values().length],
                    schemaRef,
                    ContentRef.of(initialContent.hash()),
                    initialContent.hash(),
                    "user1");

            for (int v = 1; v < versionsPerDocument; v++) {
                Content newContent = Content.of(("doc" + d + "v" + v).getBytes(), Format.XML);
                documentSet.addVersion(
                        doc.id(),
                        ContentRef.of(newContent.hash()),
                        newContent.hash(),
                        "user");
            }
        }

        // Verify document count
        assertThat(documentSet.documentCount())
                .as("DocumentSet should contain the expected number of documents")
                .isEqualTo(documentCount);

        // Verify each document has the expected number of versions
        for (Document doc : documentSet.getAllDocuments()) {
            assertThat(doc.versionCount())
                    .as("Each document should have the expected number of versions")
                    .isEqualTo(versionsPerDocument);
        }
    }

    /**
     * Property 14: DocumentSet Multi-Type Support
     * 
     * <p>Requirement 7.2: THE DocumentSet SHALL allow documents of different Document_Types 
     * within the same set.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 14: DocumentSet allows different document types")
    void documentSetAllowsDifferentDocumentTypes(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypeLists") List<DocumentType> types) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);

        // Add documents of different types
        for (int i = 0; i < types.size(); i++) {
            Content content = Content.of(("doc" + i).getBytes(), Format.XML);
            documentSet.addDocument(
                    types.get(i),
                    schemaRef,
                    ContentRef.of(content.hash()),
                    content.hash(),
                    "user1");
        }

        // Verify all documents were added
        assertThat(documentSet.documentCount())
                .as("All documents should be added regardless of type")
                .isEqualTo(types.size());

        // Verify documents can be retrieved by type
        for (DocumentType type : types.stream().distinct().toList()) {
            long expectedCount = types.stream().filter(t -> t == type).count();
            assertThat(documentSet.getDocumentsByType(type))
                    .as("Documents of type %s should be retrievable", type)
                    .hasSize((int) expectedCount);
        }
    }

    /**
     * Property 14: DocumentSet Multi-Type Support
     * 
     * <p>Requirement 7.3: THE DocumentSet SHALL provide access to all documents and 
     * their current (latest) versions.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 14: DocumentSet provides access to all documents and current versions")
    void documentSetProvidesAccessToAllDocumentsAndCurrentVersions(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 1, max = 5) int documentCount,
            @ForAll @IntRange(min = 1, max = 4) int versionsPerDocument) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);
        DocumentId[] docIds = new DocumentId[documentCount];

        // Add documents with multiple versions
        for (int d = 0; d < documentCount; d++) {
            Content initialContent = Content.of(("doc" + d).getBytes(), Format.XML);
            Document doc = documentSet.addDocument(
                    DocumentType.INVOICE,
                    schemaRef,
                    ContentRef.of(initialContent.hash()),
                    initialContent.hash(),
                    "user1");
            docIds[d] = doc.id();

            for (int v = 1; v < versionsPerDocument; v++) {
                Content newContent = Content.of(("doc" + d + "v" + v).getBytes(), Format.XML);
                documentSet.addVersion(
                        doc.id(),
                        ContentRef.of(newContent.hash()),
                        newContent.hash(),
                        "user");
            }
        }

        // Verify access to all documents
        List<Document> allDocuments = documentSet.getAllDocuments();
        assertThat(allDocuments)
                .as("getAllDocuments should return all documents")
                .hasSize(documentCount);

        // Verify each document's current version is the latest
        for (DocumentId docId : docIds) {
            Document doc = documentSet.getDocument(docId).orElseThrow();
            DocumentVersion currentVersion = doc.getCurrentVersion();
            
            assertThat(currentVersion.versionNumber())
                    .as("Current version should be the latest version")
                    .isEqualTo(versionsPerDocument);
        }
    }

    /**
     * Property 14: DocumentSet Multi-Type Support
     * 
     * <p>Requirement 7.7: THE DocumentSet SHALL support adding new documents to an existing set.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 14: DocumentSet supports adding new documents")
    void documentSetSupportsAddingNewDocuments(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 1, max = 10) int totalDocuments) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);

        // Add documents one by one
        for (int i = 0; i < totalDocuments; i++) {
            int countBefore = documentSet.documentCount();
            
            Content content = Content.of(("doc" + i).getBytes(), Format.XML);
            Document newDoc = documentSet.addDocument(
                    DocumentType.values()[i % DocumentType.values().length],
                    schemaRef,
                    ContentRef.of(content.hash()),
                    content.hash(),
                    "user1");

            // Verify document was added
            assertThat(documentSet.documentCount())
                    .as("Document count should increase by 1")
                    .isEqualTo(countBefore + 1);
            
            assertThat(documentSet.containsDocument(newDoc.id()))
                    .as("New document should be contained in the set")
                    .isTrue();
            
            assertThat(documentSet.getDocument(newDoc.id()))
                    .as("New document should be retrievable")
                    .isPresent();
        }

        // Final verification
        assertThat(documentSet.documentCount())
                .as("Final document count should match total added")
                .isEqualTo(totalDocuments);
    }

    /**
     * Property 14: DocumentSet Multi-Type Support
     * 
     * <p>All document types can coexist and be filtered correctly.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 14: All document types coexist and filter correctly")
    void allDocumentTypesCoexistAndFilterCorrectly(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);

        // Add one document of each type
        for (DocumentType type : DocumentType.values()) {
            Content content = Content.of(("doc-" + type).getBytes(), Format.XML);
            documentSet.addDocument(
                    type,
                    schemaRef,
                    ContentRef.of(content.hash()),
                    content.hash(),
                    "user1");
        }

        // Verify total count
        assertThat(documentSet.documentCount())
                .as("Should have one document per type")
                .isEqualTo(DocumentType.values().length);

        // Verify filtering by each type returns exactly one document
        for (DocumentType type : DocumentType.values()) {
            List<Document> filtered = documentSet.getDocumentsByType(type);
            assertThat(filtered)
                    .as("Filtering by %s should return exactly one document", type)
                    .hasSize(1);
            assertThat(filtered.get(0).type())
                    .as("Filtered document should have correct type")
                    .isEqualTo(type);
        }
    }

    /**
     * Property 14: DocumentSet Multi-Type Support
     * 
     * <p>Empty type filter returns empty list.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 14: Filtering by absent type returns empty list")
    void filteringByAbsentTypeReturnsEmptyList(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType presentType,
            @ForAll("documentTypes") DocumentType queryType) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);

        // Add only one type of document
        Content content = Content.of(("doc").getBytes(), Format.XML);
        documentSet.addDocument(
                presentType,
                schemaRef,
                ContentRef.of(content.hash()),
                content.hash(),
                "user1");

        // Query for a different type
        List<Document> filtered = documentSet.getDocumentsByType(queryType);
        
        if (presentType == queryType) {
            assertThat(filtered)
                    .as("Filtering by present type should return the document")
                    .hasSize(1);
        } else {
            assertThat(filtered)
                    .as("Filtering by absent type should return empty list")
                    .isEmpty();
        }
    }

    /**
     * Property 14: DocumentSet Multi-Type Support
     * 
     * <p>DocumentSet metadata is preserved.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 14: DocumentSet metadata is preserved")
    void documentSetMetadataIsPreserved(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 1, max = 5) int documentCount) {
        
        Map<String, String> metadata = Map.of(
                "project", "test-project",
                "department", "engineering");
        
        DocumentSet documentSet = DocumentSet.create("creator-user", metadata);

        // Add documents
        for (int i = 0; i < documentCount; i++) {
            Content content = Content.of(("doc" + i).getBytes(), Format.XML);
            documentSet.addDocument(
                    DocumentType.INVOICE,
                    schemaRef,
                    ContentRef.of(content.hash()),
                    content.hash(),
                    "user");
        }

        // Verify metadata is preserved
        assertThat(documentSet.metadata())
                .as("Metadata should be preserved")
                .containsEntry("project", "test-project")
                .containsEntry("department", "engineering");
        
        assertThat(documentSet.createdBy())
                .as("CreatedBy should be preserved")
                .isEqualTo("creator-user");
    }
}
