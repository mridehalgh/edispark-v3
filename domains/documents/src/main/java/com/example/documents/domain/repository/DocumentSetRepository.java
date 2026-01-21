package com.example.documents.domain.repository;

import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DocumentSet aggregate persistence.
 * 
 * <p>This is a port in the hexagonal architecture. Implementations are provided
 * in the infrastructure layer (e.g., DynamoDB).</p>
 * 
 * <p>Requirements: 10.1, 10.2, 10.5</p>
 */
public interface DocumentSetRepository {

    /**
     * Finds a DocumentSet by its unique identifier.
     * 
     * <p>Requirement 10.1: Support retrieving a DocumentSet by its identifier.</p>
     * 
     * @param id the DocumentSet identifier
     * @return the DocumentSet if found, empty otherwise
     */
    Optional<DocumentSet> findById(DocumentSetId id);

    /**
     * Returns all DocumentSets.
     * 
     * <p>Requirement 10.2: Support listing all DocumentSets.</p>
     * 
     * @return a list of all DocumentSets
     */
    List<DocumentSet> findAll();

    /**
     * Persists a DocumentSet (create or update).
     * 
     * <p>The entire aggregate (including all documents, versions, and derivatives)
     * is persisted atomically.</p>
     * 
     * @param documentSet the DocumentSet to save
     */
    void save(DocumentSet documentSet);

    /**
     * Deletes a DocumentSet by its identifier.
     * 
     * <p>This removes the entire aggregate including all documents, versions,
     * and derivatives. Content in the ContentStore is not automatically deleted
     * as it may be referenced by other documents.</p>
     * 
     * @param id the DocumentSet identifier to delete
     */
    void delete(DocumentSetId id);

    /**
     * Finds DocumentSets containing documents with the specified content hash.
     * 
     * <p>Requirement 10.5: Support querying documents by ContentHash for duplicate detection.</p>
     * 
     * @param contentHash the content hash to search for
     * @return a list of DocumentSets containing documents with the given hash
     */
    List<DocumentSet> findByContentHash(ContentHash contentHash);
}
