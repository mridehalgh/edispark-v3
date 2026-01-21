package com.example.documents.infrastructure.storage;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.repository.ContentStore;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for content deduplication in the ContentStore.
 * 
 * <p><b>Property 6: Content Deduplication</b></p>
 * <p><b>Validates: Requirements 8.2, 8.5</b></p>
 * 
 * <p>For any two Content objects with identical byte arrays, storing both SHALL 
 * result in only one copy in the ContentStore, and both SHALL share the same ContentHash.</p>
 */
class ContentDeduplicationPropertyTest {

    private Path tempDir;
    private ContentStore store;

    @BeforeTry
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("content-dedup-test");
        store = new FileSystemContentStore(tempDir);
    }

    @AfterTry
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        }
    }

    /**
     * Property 6: Content Deduplication
     * 
     * <p>For any two Content objects with identical byte arrays, storing both SHALL 
     * result in only one copy in the ContentStore, and both SHALL share the same ContentHash.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-domain, Property 6: Identical content produces same hash")
    void identicalContentProducesSameHash(
            @ForAll @Size(min = 0, max = 10000) byte[] data) {
        
        Content content1 = Content.of(data, Format.XML);
        Content content2 = Content.of(data, Format.JSON);

        // Both should have the same hash regardless of format
        assertThat(content1.hash()).isEqualTo(content2.hash());
    }

    /**
     * Verifies that storing identical content twice results in deduplication.
     */
    @Property(tries = 100)
    @Label("Feature: documents-domain, Property 6: Storing identical content is idempotent")
    void storingIdenticalContentIsIdempotent(
            @ForAll @Size(min = 0, max = 5000) byte[] data) {
        
        Content content1 = Content.of(data, Format.XML);
        Content content2 = Content.of(data, Format.JSON);

        store.store(content1);
        store.store(content2);

        // Only one file should exist in the store
        long fileCount = countFilesInStore();
        assertThat(fileCount).isEqualTo(1);

        // Both should retrieve the same data
        Optional<byte[]> retrieved1 = store.retrieve(content1.hash());
        Optional<byte[]> retrieved2 = store.retrieve(content2.hash());

        assertThat(retrieved1).isPresent();
        assertThat(retrieved2).isPresent();
        assertThat(retrieved1.get()).isEqualTo(data);
        assertThat(retrieved2.get()).isEqualTo(data);
    }

    /**
     * Verifies that storing the same content multiple times doesn't create duplicates.
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 6: Multiple stores of same content create single file")
    void multipleStoresOfSameContentCreateSingleFile(
            @ForAll @Size(min = 1, max = 5000) byte[] data) {
        
        Content content = Content.of(data, Format.PDF);

        // Store the same content multiple times
        store.store(content);
        store.store(content);
        store.store(content);
        store.store(content);
        store.store(content);

        // Only one file should exist
        long fileCount = countFilesInStore();
        assertThat(fileCount).isEqualTo(1);
    }

    /**
     * Verifies that different content creates different files.
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 6: Different content creates different files")
    void differentContentCreatesDifferentFiles(
            @ForAll @Size(min = 1, max = 1000) byte[] data1,
            @ForAll @Size(min = 1, max = 1000) byte[] data2) {
        
        // Skip if data happens to be identical
        if (java.util.Arrays.equals(data1, data2)) {
            return;
        }

        Content content1 = Content.of(data1, Format.XML);
        Content content2 = Content.of(data2, Format.XML);

        store.store(content1);
        store.store(content2);

        // Two different files should exist
        long fileCount = countFilesInStore();
        assertThat(fileCount).isEqualTo(2);

        // Each should retrieve its own data
        Optional<byte[]> retrieved1 = store.retrieve(content1.hash());
        Optional<byte[]> retrieved2 = store.retrieve(content2.hash());

        assertThat(retrieved1).isPresent();
        assertThat(retrieved2).isPresent();
        assertThat(retrieved1.get()).isEqualTo(data1);
        assertThat(retrieved2.get()).isEqualTo(data2);
    }

    /**
     * Verifies that content with same hash can be reused after deletion and re-store.
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 6: Content can be re-stored after deletion")
    void contentCanBeRestoredAfterDeletion(
            @ForAll @Size(min = 1, max = 5000) byte[] data) {
        
        Content content = Content.of(data, Format.EDI);

        // Store, delete, then store again
        store.store(content);
        assertThat(store.exists(content.hash())).isTrue();
        
        store.delete(content.hash());
        assertThat(store.exists(content.hash())).isFalse();
        
        store.store(content);
        assertThat(store.exists(content.hash())).isTrue();

        // Should still retrieve correctly
        Optional<byte[]> retrieved = store.retrieve(content.hash());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo(data);
    }

    private long countFilesInStore() {
        try {
            return Files.list(tempDir).count();
        } catch (IOException e) {
            throw new RuntimeException("Failed to count files in store", e);
        }
    }
}
