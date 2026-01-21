package com.example.documents.domain.model;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for document identity stability.
 * 
 * <p><b>Property 1: Document Identity Stability</b></p>
 * <p>For any Document within a DocumentSet, creating new versions or derivatives 
 * SHALL NOT change the Document's identifier.</p>
 * 
 * <p><b>Validates: Requirements 1.1</b></p>
 */
class DocumentIdentityStabilityPropertyTest {

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
    Arbitrary<Format> formats() {
        return Arbitraries.of(Format.values());
    }

    @Provide
    Arbitrary<TransformationMethod> transformationMethods() {
        return Arbitraries.of(TransformationMethod.values());
    }

    /**
     * Property 1: Document Identity Stability
     * 
     * <p>Adding new versions to a document SHALL NOT change its identifier.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 1: Adding versions does not change document identity")
    void addingVersionsDoesNotChangeDocumentIdentity(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll @IntRange(min = 1, max = 10) int additionalVersions) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        DocumentSet documentSet = DocumentSet.create("user1", null);
        
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");
        
        DocumentId originalId = document.id();

        // Add multiple versions
        for (int i = 0; i < additionalVersions; i++) {
            Content newContent = Content.of(("version" + (i + 2)).getBytes(), Format.XML);
            documentSet.addVersion(
                    document.id(),
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user" + (i + 2));
        }

        // Verify identity remains stable
        assertThat(document.id())
                .as("Document ID should remain stable after adding versions")
                .isEqualTo(originalId);
        
        // Verify document can still be retrieved by original ID
        assertThat(documentSet.getDocument(originalId))
                .as("Document should be retrievable by original ID")
                .isPresent()
                .hasValueSatisfying(d -> assertThat(d.id()).isEqualTo(originalId));
    }

    /**
     * Property 1: Document Identity Stability
     * 
     * <p>Adding derivatives to a document SHALL NOT change its identifier.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 1: Adding derivatives does not change document identity")
    void addingDerivativesDoesNotChangeDocumentIdentity(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll("transformationMethods") TransformationMethod method) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        DocumentSet documentSet = DocumentSet.create("user1", null);
        
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");
        
        DocumentId originalId = document.id();
        DocumentVersionId versionId = document.getCurrentVersion().id();

        // Add derivatives in different formats
        for (Format format : Format.values()) {
            if (format != Format.XML) { // Skip source format
                Content derivativeContent = Content.of(("derivative-" + format).getBytes(), format);
                documentSet.createDerivative(
                        document.id(),
                        versionId,
                        format,
                        ContentRef.of(derivativeContent.hash()),
                        derivativeContent.hash(),
                        method);
            }
        }

        // Verify identity remains stable
        assertThat(document.id())
                .as("Document ID should remain stable after adding derivatives")
                .isEqualTo(originalId);
        
        // Verify document can still be retrieved by original ID
        assertThat(documentSet.getDocument(originalId))
                .as("Document should be retrievable by original ID")
                .isPresent()
                .hasValueSatisfying(d -> assertThat(d.id()).isEqualTo(originalId));
    }

    /**
     * Property 1: Document Identity Stability
     * 
     * <p>Adding both versions and derivatives SHALL NOT change document identity.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 1: Combined operations do not change document identity")
    void combinedOperationsDoNotChangeDocumentIdentity(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll @IntRange(min = 1, max = 5) int additionalVersions,
            @ForAll("transformationMethods") TransformationMethod method) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        DocumentSet documentSet = DocumentSet.create("user1", null);
        
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");
        
        DocumentId originalId = document.id();

        // Add versions and derivatives interleaved
        for (int i = 0; i < additionalVersions; i++) {
            // Add a version
            Content versionContent = Content.of(("version" + (i + 2)).getBytes(), Format.XML);
            DocumentVersion newVersion = documentSet.addVersion(
                    document.id(),
                    ContentRef.of(versionContent.hash()),
                    versionContent.hash(),
                    "user");
            
            // Add a derivative for this version
            Content derivativeContent = Content.of(("derivative" + i).getBytes(), Format.JSON);
            documentSet.createDerivative(
                    document.id(),
                    newVersion.id(),
                    Format.JSON,
                    ContentRef.of(derivativeContent.hash()),
                    derivativeContent.hash(),
                    method);
        }

        // Verify identity remains stable
        assertThat(document.id())
                .as("Document ID should remain stable after combined operations")
                .isEqualTo(originalId);
    }

    /**
     * Property 1: Document Identity Stability
     * 
     * <p>Document identity is stable across multiple documents in a DocumentSet.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 1: Multiple documents maintain stable identities")
    void multipleDocumentsMaintainStableIdentities(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 2, max = 5) int documentCount) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);
        DocumentId[] originalIds = new DocumentId[documentCount];

        // Create multiple documents
        for (int i = 0; i < documentCount; i++) {
            Content content = Content.of(("doc" + i).getBytes(), Format.XML);
            Document doc = documentSet.addDocument(
                    DocumentType.values()[i % DocumentType.values().length],
                    schemaRef,
                    ContentRef.of(content.hash()),
                    content.hash(),
                    "user");
            originalIds[i] = doc.id();
        }

        // Add versions to all documents
        for (DocumentId docId : originalIds) {
            Content newContent = Content.of(("new-version-" + docId).getBytes(), Format.XML);
            documentSet.addVersion(
                    docId,
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user");
        }

        // Verify all identities remain stable
        for (int i = 0; i < documentCount; i++) {
            DocumentId originalId = originalIds[i];
            assertThat(documentSet.getDocument(originalId))
                    .as("Document %d should be retrievable by original ID", i)
                    .isPresent()
                    .hasValueSatisfying(d -> assertThat(d.id()).isEqualTo(originalId));
        }
    }
}
