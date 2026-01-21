package com.example.documents.infrastructure.storage;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.repository.ContentStore;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for content round-trip through the ContentStore.
 * 
 * <p><b>Property 7: Content Round-Trip</b></p>
 * <p><b>Validates: Requirements 8.4</b></p>
 * 
 * <p>For any Content stored in the ContentStore, retrieving by its ContentHash 
 * SHALL return byte-for-byte identical data.</p>
 */
class ContentRoundTripPropertyTest {

    private Path tempDir;
    private ContentStore store;

    @BeforeTry
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("content-store-test");
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
     * Property 7: Content Round-Trip
     * 
     * <p>For any Content stored in the ContentStore, retrieving by its ContentHash 
     * SHALL return byte-for-byte identical data.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-domain, Property 7: Content round-trip preserves data")
    void storedContentCanBeRetrievedIdentically(
            @ForAll @Size(min = 0, max = 10000) byte[] data,
            @ForAll Format format) {
        
        Content content = Content.of(data, format);

        store.store(content);
        Optional<byte[]> retrieved = store.retrieve(content.hash());

        assertThat(retrieved)
            .isPresent()
            .hasValueSatisfying(retrievedData -> 
                assertThat(retrievedData).isEqualTo(data));
    }

    /**
     * Verifies that content can be retrieved multiple times with identical results.
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 7: Multiple retrievals return identical data")
    void multipleRetrievalsReturnIdenticalData(
            @ForAll @Size(min = 1, max = 5000) byte[] data) {
        
        Content content = Content.of(data, Format.JSON);

        store.store(content);
        
        Optional<byte[]> retrieved1 = store.retrieve(content.hash());
        Optional<byte[]> retrieved2 = store.retrieve(content.hash());
        Optional<byte[]> retrieved3 = store.retrieve(content.hash());

        assertThat(retrieved1).isPresent();
        assertThat(retrieved2).isPresent();
        assertThat(retrieved3).isPresent();
        
        assertThat(retrieved1.get()).isEqualTo(data);
        assertThat(retrieved2.get()).isEqualTo(data);
        assertThat(retrieved3.get()).isEqualTo(data);
    }

    /**
     * Verifies that retrieving non-existent content returns empty.
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 7: Non-existent content returns empty")
    void retrievingNonExistentContentReturnsEmpty(
            @ForAll @Size(min = 1, max = 1000) byte[] data) {
        
        Content content = Content.of(data, Format.XML);
        
        // Don't store, just try to retrieve
        Optional<byte[]> retrieved = store.retrieve(content.hash());

        assertThat(retrieved).isEmpty();
    }

    /**
     * Verifies that exists() correctly reports content presence after store.
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 7: Exists returns true after store")
    void existsReturnsTrueAfterStore(
            @ForAll @Size(min = 0, max = 5000) byte[] data) {
        
        Content content = Content.of(data, Format.PDF);

        assertThat(store.exists(content.hash())).isFalse();
        
        store.store(content);
        
        assertThat(store.exists(content.hash())).isTrue();
    }

    /**
     * Verifies that delete removes content and subsequent retrieval returns empty.
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 7: Delete removes content")
    void deleteRemovesContent(
            @ForAll @Size(min = 1, max = 5000) byte[] data) {
        
        Content content = Content.of(data, Format.EDI);

        store.store(content);
        assertThat(store.exists(content.hash())).isTrue();
        
        store.delete(content.hash());
        
        assertThat(store.exists(content.hash())).isFalse();
        assertThat(store.retrieve(content.hash())).isEmpty();
    }
}
