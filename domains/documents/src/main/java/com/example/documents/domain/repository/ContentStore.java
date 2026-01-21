package com.example.documents.domain.repository;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentHash;

import java.util.Optional;

/**
 * Content-addressable storage interface for document and schema content.
 * 
 * <p>This is a port in the hexagonal architecture. Implementations are provided
 * in the infrastructure layer (e.g., S3, file system).</p>
 * 
 * <p>Content is stored by its hash, enabling deduplication. If content with the
 * same hash already exists, the store operation is idempotent.</p>
 * 
 * <p>Requirements: 8.1, 8.2, 8.4, 8.5</p>
 */
public interface ContentStore {

    /**
     * Stores content in the content store.
     * 
     * <p>Requirement 8.1: Store document content separately from document metadata.</p>
     * <p>Requirement 8.2: Support content deduplication using ContentHash.</p>
     * <p>Requirement 8.5: If content with the same hash already exists, reuse it.</p>
     * 
     * <p>This operation is idempotent. If content with the same hash already exists,
     * the existing content is retained and no error is raised.</p>
     * 
     * @param content the content to store
     */
    void store(Content content);

    /**
     * Retrieves content by its hash.
     * 
     * <p>Requirement 8.4: Support retrieval of content by ContentHash.</p>
     * 
     * @param hash the content hash to retrieve
     * @return the content data if found, empty otherwise
     */
    Optional<byte[]> retrieve(ContentHash hash);

    /**
     * Checks if content with the given hash exists in the store.
     * 
     * <p>This can be used to check for duplicates before storing or to verify
     * content availability.</p>
     * 
     * @param hash the content hash to check
     * @return true if content with the hash exists
     */
    boolean exists(ContentHash hash);

    /**
     * Deletes content by its hash.
     * 
     * <p>Note: Content should only be deleted when no documents reference it.
     * This is typically handled by a garbage collection process rather than
     * direct deletion.</p>
     * 
     * @param hash the content hash to delete
     */
    void delete(ContentHash hash);
}
