package com.example.documents.infrastructure.storage;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.repository.ContentStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * File system based implementation of ContentStore for local development.
 * 
 * <p>Stores content by hash in a local directory structure. Content is stored
 * using the hash as the filename, enabling content-addressable storage and
 * automatic deduplication.</p>
 * 
 * <p>Requirements: 8.1, 8.2, 8.4, 8.5</p>
 */
public class FileSystemContentStore implements ContentStore {

    private final Path baseDirectory;

    /**
     * Creates a FileSystemContentStore with the specified base directory.
     * 
     * @param baseDirectory the directory where content will be stored
     */
    public FileSystemContentStore(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
        ensureDirectoryExists();
    }

    /**
     * Stores content in the file system.
     * 
     * <p>Requirement 8.1: Store document content separately from document metadata.</p>
     * <p>Requirement 8.2: Support content deduplication using ContentHash.</p>
     * <p>Requirement 8.5: If content with the same hash already exists, reuse it.</p>
     * 
     * @param content the content to store
     */
    @Override
    public void store(Content content) {
        Path contentPath = resolveContentPath(content.hash());
        
        // Idempotent: if content already exists, skip writing
        if (Files.exists(contentPath)) {
            return;
        }

        try {
            Files.write(contentPath, content.data());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store content: " + content.hash(), e);
        }
    }

    /**
     * Retrieves content by its hash.
     * 
     * <p>Requirement 8.4: Support retrieval of content by ContentHash.</p>
     * 
     * @param hash the content hash to retrieve
     * @return the content data if found, empty otherwise
     */
    @Override
    public Optional<byte[]> retrieve(ContentHash hash) {
        Path contentPath = resolveContentPath(hash);
        
        if (!Files.exists(contentPath)) {
            return Optional.empty();
        }

        try {
            byte[] data = Files.readAllBytes(contentPath);
            return Optional.of(data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to retrieve content: " + hash, e);
        }
    }

    /**
     * Checks if content with the given hash exists in the store.
     * 
     * @param hash the content hash to check
     * @return true if content with the hash exists
     */
    @Override
    public boolean exists(ContentHash hash) {
        Path contentPath = resolveContentPath(hash);
        return Files.exists(contentPath);
    }

    /**
     * Deletes content by its hash.
     * 
     * @param hash the content hash to delete
     */
    @Override
    public void delete(ContentHash hash) {
        Path contentPath = resolveContentPath(hash);
        
        try {
            Files.deleteIfExists(contentPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete content: " + hash, e);
        }
    }

    /**
     * Resolves the file path for a given content hash.
     * Uses the full hash string as the filename.
     */
    private Path resolveContentPath(ContentHash hash) {
        String filename = hash.toFullString().replace(":", "_");
        return baseDirectory.resolve(filename);
    }

    /**
     * Ensures the base directory exists, creating it if necessary.
     */
    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(baseDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create content store directory: " + baseDirectory, e);
        }
    }
}
