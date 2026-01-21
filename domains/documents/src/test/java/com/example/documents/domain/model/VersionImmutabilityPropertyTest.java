package com.example.documents.domain.model;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for version immutability.
 * 
 * <p><b>Property 2: Version Immutability</b></p>
 * <p>For any DocumentVersion, once created, its content hash, version number, and creation 
 * timestamp SHALL remain unchanged regardless of subsequent operations on the Document or DocumentSet.</p>
 * 
 * <p><b>Validates: Requirements 2.1, 2.7</b></p>
 */
class VersionImmutabilityPropertyTest {

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
    Arbitrary<TransformationMethod> transformationMethods() {
        return Arbitraries.of(TransformationMethod.values());
    }

    /**
     * Property 2: Version Immutability
     * 
     * <p>Content hash remains unchanged after adding new versions.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 2: Content hash remains unchanged after adding versions")
    void contentHashRemainsUnchangedAfterAddingVersions(
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
        
        // Capture original version properties
        DocumentVersion originalVersion = document.getVersion(1).orElseThrow();
        ContentHash originalHash = originalVersion.contentHash();
        int originalVersionNumber = originalVersion.versionNumber();
        Instant originalCreatedAt = originalVersion.createdAt();

        // Add multiple new versions
        for (int i = 0; i < additionalVersions; i++) {
            Content newContent = Content.of(("version" + (i + 2)).getBytes(), Format.XML);
            documentSet.addVersion(
                    document.id(),
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user" + (i + 2));
        }

        // Verify original version properties remain unchanged
        DocumentVersion retrievedVersion = document.getVersion(1).orElseThrow();
        assertThat(retrievedVersion.contentHash())
                .as("Content hash should remain unchanged")
                .isEqualTo(originalHash);
        assertThat(retrievedVersion.versionNumber())
                .as("Version number should remain unchanged")
                .isEqualTo(originalVersionNumber);
        assertThat(retrievedVersion.createdAt())
                .as("Creation timestamp should remain unchanged")
                .isEqualTo(originalCreatedAt);
    }

    /**
     * Property 2: Version Immutability
     * 
     * <p>All version properties remain unchanged after adding derivatives.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 2: Version properties unchanged after adding derivatives")
    void versionPropertiesUnchangedAfterAddingDerivatives(
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
        
        // Capture original version properties
        DocumentVersion originalVersion = document.getCurrentVersion();
        ContentHash originalHash = originalVersion.contentHash();
        int originalVersionNumber = originalVersion.versionNumber();
        Instant originalCreatedAt = originalVersion.createdAt();
        DocumentVersionId originalId = originalVersion.id();

        // Add derivatives in different formats
        for (Format format : Format.values()) {
            if (format != Format.XML) {
                Content derivativeContent = Content.of(("derivative-" + format).getBytes(), format);
                documentSet.createDerivative(
                        document.id(),
                        originalVersion.id(),
                        format,
                        ContentRef.of(derivativeContent.hash()),
                        derivativeContent.hash(),
                        method);
            }
        }

        // Verify version properties remain unchanged
        DocumentVersion retrievedVersion = document.getVersionById(originalId).orElseThrow();
        assertThat(retrievedVersion.contentHash())
                .as("Content hash should remain unchanged after adding derivatives")
                .isEqualTo(originalHash);
        assertThat(retrievedVersion.versionNumber())
                .as("Version number should remain unchanged after adding derivatives")
                .isEqualTo(originalVersionNumber);
        assertThat(retrievedVersion.createdAt())
                .as("Creation timestamp should remain unchanged after adding derivatives")
                .isEqualTo(originalCreatedAt);
    }

    /**
     * Property 2: Version Immutability
     * 
     * <p>All historical versions remain immutable after subsequent operations.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 2: All historical versions remain immutable")
    void allHistoricalVersionsRemainImmutable(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 2, max = 8) int totalVersions) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);
        Content initialContent = Content.of(new byte[]{1}, Format.XML);
        
        Document document = documentSet.addDocument(
                DocumentType.INVOICE,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");

        // Create versions and capture their properties
        ContentHash[] originalHashes = new ContentHash[totalVersions];
        int[] originalVersionNumbers = new int[totalVersions];
        Instant[] originalTimestamps = new Instant[totalVersions];

        DocumentVersion firstVersion = document.getVersion(1).orElseThrow();
        originalHashes[0] = firstVersion.contentHash();
        originalVersionNumbers[0] = firstVersion.versionNumber();
        originalTimestamps[0] = firstVersion.createdAt();

        for (int i = 1; i < totalVersions; i++) {
            Content newContent = Content.of(("v" + (i + 1)).getBytes(), Format.XML);
            DocumentVersion newVersion = documentSet.addVersion(
                    document.id(),
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user");
            originalHashes[i] = newVersion.contentHash();
            originalVersionNumbers[i] = newVersion.versionNumber();
            originalTimestamps[i] = newVersion.createdAt();
        }

        // Verify all versions remain immutable
        for (int i = 0; i < totalVersions; i++) {
            int versionNum = i + 1;
            DocumentVersion version = document.getVersion(versionNum).orElseThrow();
            
            assertThat(version.contentHash())
                    .as("Version %d content hash should remain unchanged", versionNum)
                    .isEqualTo(originalHashes[i]);
            assertThat(version.versionNumber())
                    .as("Version %d number should remain unchanged", versionNum)
                    .isEqualTo(originalVersionNumbers[i]);
            assertThat(version.createdAt())
                    .as("Version %d timestamp should remain unchanged", versionNum)
                    .isEqualTo(originalTimestamps[i]);
        }
    }

    /**
     * Property 2: Version Immutability
     * 
     * <p>Version immutability is preserved across multiple documents in a DocumentSet.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 2: Version immutability across multiple documents")
    void versionImmutabilityAcrossMultipleDocuments(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 2, max = 4) int documentCount,
            @ForAll @IntRange(min = 1, max = 3) int versionsPerDocument) {
        
        DocumentSet documentSet = DocumentSet.create("user1", null);
        
        // Create documents and capture version properties
        DocumentId[] docIds = new DocumentId[documentCount];
        ContentHash[][] originalHashes = new ContentHash[documentCount][versionsPerDocument];
        
        for (int d = 0; d < documentCount; d++) {
            Content initialContent = Content.of(("doc" + d).getBytes(), Format.XML);
            Document doc = documentSet.addDocument(
                    DocumentType.values()[d % DocumentType.values().length],
                    schemaRef,
                    ContentRef.of(initialContent.hash()),
                    initialContent.hash(),
                    "user");
            docIds[d] = doc.id();
            originalHashes[d][0] = doc.getCurrentVersion().contentHash();
            
            for (int v = 1; v < versionsPerDocument; v++) {
                Content newContent = Content.of(("doc" + d + "v" + v).getBytes(), Format.XML);
                DocumentVersion newVersion = documentSet.addVersion(
                        doc.id(),
                        ContentRef.of(newContent.hash()),
                        newContent.hash(),
                        "user");
                originalHashes[d][v] = newVersion.contentHash();
            }
        }

        // Verify all versions across all documents remain immutable
        for (int d = 0; d < documentCount; d++) {
            Document doc = documentSet.getDocument(docIds[d]).orElseThrow();
            for (int v = 0; v < versionsPerDocument; v++) {
                DocumentVersion version = doc.getVersion(v + 1).orElseThrow();
                assertThat(version.contentHash())
                        .as("Document %d, Version %d content hash should remain unchanged", d, v + 1)
                        .isEqualTo(originalHashes[d][v]);
            }
        }
    }
}
