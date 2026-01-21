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
 * Property-based tests for version sequence integrity.
 * 
 * <p><b>Validates: Requirements 2.2, 2.3, 2.4, 2.5</b></p>
 */
class DocumentVersionSequencePropertyTest {

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
    Arbitrary<Content> contents() {
        return Arbitraries.bytes().array(byte[].class)
                .ofMinSize(1).ofMaxSize(1000)
                .map(data -> Content.of(data, Format.XML));
    }

    /**
     * Property 3: Version Sequence Integrity
     * 
     * <p>For any Document with multiple versions, the version numbers SHALL form a 
     * contiguous sequence starting from 1.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 3: Version numbers form contiguous sequence starting from 1")
    void versionNumbersFormContiguousSequence(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 1, max = 10) int additionalVersions) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        Document document = Document.create(
                DocumentType.INVOICE,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user1");

        // Add additional versions
        for (int i = 0; i < additionalVersions; i++) {
            Content newContent = Content.of(("version" + (i + 2)).getBytes(), Format.XML);
            document.addVersion(
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user" + (i + 2));
        }

        // Verify contiguous sequence starting from 1
        int expectedVersionCount = additionalVersions + 1;
        assertThat(document.versionCount()).isEqualTo(expectedVersionCount);
        
        for (int versionNum = 1; versionNum <= expectedVersionCount; versionNum++) {
            assertThat(document.getVersion(versionNum))
                    .as("Version %d should exist", versionNum)
                    .isPresent();
            assertThat(document.getVersion(versionNum).get().versionNumber())
                    .isEqualTo(versionNum);
        }
    }

    /**
     * Property 3: Version Sequence Integrity
     * 
     * <p>Each version (except the first) SHALL reference its predecessor.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 3: Each version references its predecessor")
    void eachVersionReferencesItsPredecessor(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 1, max = 10) int additionalVersions) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        Document document = Document.create(
                DocumentType.ORDER,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "creator");

        // Add additional versions
        for (int i = 0; i < additionalVersions; i++) {
            Content newContent = Content.of(("v" + (i + 2)).getBytes(), Format.XML);
            document.addVersion(
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "editor");
        }

        // Verify predecessor references
        for (int versionNum = 1; versionNum <= document.versionCount(); versionNum++) {
            DocumentVersion version = document.getVersion(versionNum).orElseThrow();
            
            if (versionNum == 1) {
                assertThat(version.previousVersion())
                        .as("First version should have no predecessor")
                        .isNull();
            } else {
                DocumentVersion predecessor = document.getVersion(versionNum - 1).orElseThrow();
                assertThat(version.previousVersion())
                        .as("Version %d should reference version %d", versionNum, versionNum - 1)
                        .isEqualTo(predecessor.id());
            }
        }
    }

    /**
     * Property 3: Version Sequence Integrity
     * 
     * <p>The system SHALL allow retrieval of any historical version by version number.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 3: Historical versions are retrievable by number")
    void historicalVersionsAreRetrievableByNumber(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 0, max = 15) int additionalVersions) {
        
        Content initialContent = Content.of(new byte[]{10, 20, 30}, Format.JSON);
        Document document = Document.create(
                DocumentType.QUOTATION,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "author");

        // Track content hashes for each version
        ContentHash[] expectedHashes = new ContentHash[additionalVersions + 1];
        expectedHashes[0] = initialContent.hash();

        for (int i = 0; i < additionalVersions; i++) {
            Content newContent = Content.of(("content-" + (i + 2)).getBytes(), Format.JSON);
            expectedHashes[i + 1] = newContent.hash();
            document.addVersion(
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "author");
        }

        // Verify each version is retrievable and has correct content hash
        for (int versionNum = 1; versionNum <= document.versionCount(); versionNum++) {
            DocumentVersion version = document.getVersion(versionNum).orElseThrow();
            assertThat(version.contentHash())
                    .as("Version %d should have correct content hash", versionNum)
                    .isEqualTo(expectedHashes[versionNum - 1]);
        }
    }

    /**
     * Property 3: Version Sequence Integrity
     * 
     * <p>The DocumentSet SHALL maintain the complete version history of a document.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 3: Complete version history is maintained")
    void completeVersionHistoryIsMaintained(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 0, max = 20) int additionalVersions) {
        
        Content initialContent = Content.of(new byte[]{1}, Format.XML);
        Document document = Document.create(
                DocumentType.CREDIT_NOTE,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user");

        for (int i = 0; i < additionalVersions; i++) {
            Content newContent = Content.of(new byte[]{(byte) (i + 2)}, Format.XML);
            document.addVersion(
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user");
        }

        // Verify all versions are present
        assertThat(document.versions())
                .hasSize(additionalVersions + 1);
        
        // Verify versions list contains all version numbers
        assertThat(document.versions().stream().map(DocumentVersion::versionNumber).toList())
                .containsExactlyInAnyOrder(
                        java.util.stream.IntStream.rangeClosed(1, additionalVersions + 1)
                                .boxed()
                                .toArray(Integer[]::new));
    }
}
