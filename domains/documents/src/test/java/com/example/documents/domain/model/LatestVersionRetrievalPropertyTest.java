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
 * Property-based tests for latest version retrieval.
 * 
 * <p><b>Validates: Requirements 2.6</b></p>
 */
class LatestVersionRetrievalPropertyTest {

    @Provide
    Arbitrary<SchemaVersionRef> schemaVersionRefs() {
        return Arbitraries.of(
                SchemaVersionRef.of(
                        new SchemaId(UUID.randomUUID()),
                        new VersionIdentifier("1.0.0")),
                SchemaVersionRef.of(
                        new SchemaId(UUID.randomUUID()),
                        new VersionIdentifier("2.0.0")));
    }

    /**
     * Property 4: Latest Version Retrieval
     * 
     * <p>For any Document with N versions, calling getCurrentVersion() SHALL return 
     * the version with versionNumber equal to N.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 4: getCurrentVersion returns version with highest number")
    void getCurrentVersionReturnsVersionWithHighestNumber(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 0, max = 20) int additionalVersions) {
        
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        Document document = Document.create(
                DocumentType.INVOICE,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "creator");

        // Add additional versions
        for (int i = 0; i < additionalVersions; i++) {
            Content newContent = Content.of(("version-" + (i + 2)).getBytes(), Format.XML);
            document.addVersion(
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "editor");
        }

        int expectedVersionNumber = additionalVersions + 1;
        DocumentVersion currentVersion = document.getCurrentVersion();

        assertThat(currentVersion.versionNumber())
                .as("Current version should have version number equal to total versions")
                .isEqualTo(expectedVersionNumber);
    }

    /**
     * Property 4: Latest Version Retrieval
     * 
     * <p>After adding a new version, getCurrentVersion() SHALL return the newly added version.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 4: getCurrentVersion returns newly added version")
    void getCurrentVersionReturnsNewlyAddedVersion(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 1, max = 15) int totalVersions) {
        
        Content initialContent = Content.of(new byte[]{1}, Format.JSON);
        Document document = Document.create(
                DocumentType.ORDER,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user");

        DocumentVersion lastAddedVersion = document.getCurrentVersion();

        // Add versions and verify getCurrentVersion returns the latest each time
        for (int i = 1; i < totalVersions; i++) {
            Content newContent = Content.of(("v" + (i + 1)).getBytes(), Format.JSON);
            lastAddedVersion = document.addVersion(
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user");

            assertThat(document.getCurrentVersion())
                    .as("getCurrentVersion should return the version just added")
                    .isEqualTo(lastAddedVersion);
        }
    }

    /**
     * Property 4: Latest Version Retrieval
     * 
     * <p>getCurrentVersion() SHALL return the same version as getVersion(N) where N is the total count.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 4: getCurrentVersion equals getVersion(versionCount)")
    void getCurrentVersionEqualsGetVersionOfCount(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll @IntRange(min = 0, max = 25) int additionalVersions) {
        
        Content initialContent = Content.of(new byte[]{10, 20}, Format.XML);
        Document document = Document.create(
                DocumentType.CREDIT_NOTE,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "author");

        for (int i = 0; i < additionalVersions; i++) {
            Content newContent = Content.of(("content" + i).getBytes(), Format.XML);
            document.addVersion(
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "author");
        }

        DocumentVersion currentVersion = document.getCurrentVersion();
        DocumentVersion versionByNumber = document.getVersion(document.versionCount()).orElseThrow();

        assertThat(currentVersion)
                .as("getCurrentVersion should equal getVersion(versionCount)")
                .isEqualTo(versionByNumber);
    }

    /**
     * Property 4: Latest Version Retrieval
     * 
     * <p>For a newly created document, getCurrentVersion() SHALL return version 1.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 4: New document has current version 1")
    void newDocumentHasCurrentVersionOne(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef) {
        
        Content content = Content.of(new byte[]{1, 2, 3, 4, 5}, Format.JSON);
        Document document = Document.create(
                DocumentType.QUOTATION,
                schemaRef,
                ContentRef.of(content.hash()),
                content.hash(),
                "creator");

        DocumentVersion currentVersion = document.getCurrentVersion();

        assertThat(currentVersion.versionNumber())
                .as("New document should have current version 1")
                .isEqualTo(1);
        assertThat(currentVersion.isFirstVersion())
                .as("Current version should be first version")
                .isTrue();
    }
}
