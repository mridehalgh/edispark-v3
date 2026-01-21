package com.example.documents.domain.repository;

import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;

import java.util.Optional;

/**
 * Repository interface for Schema aggregate persistence.
 * 
 * <p>This is a port in the hexagonal architecture. Implementations are provided
 * in the infrastructure layer (e.g., DynamoDB).</p>
 * 
 * <p>Requirements: 3.6</p>
 */
public interface SchemaRepository {

    /**
     * Finds a Schema by its unique identifier.
     * 
     * @param id the Schema identifier
     * @return the Schema if found, empty otherwise
     */
    Optional<Schema> findById(SchemaId id);

    /**
     * Persists a Schema (create or update).
     * 
     * <p>The entire aggregate (including all schema versions) is persisted atomically.</p>
     * 
     * @param schema the Schema to save
     */
    void save(Schema schema);

    /**
     * Checks if a specific schema version is referenced by any documents.
     * 
     * <p>Requirement 3.6: Prevent deletion of SchemaVersions that are referenced
     * by existing documents.</p>
     * 
     * <p>This method is used to enforce referential integrity before allowing
     * schema version deletion or deprecation.</p>
     * 
     * @param schemaVersionRef the schema version reference to check
     * @return true if the schema version is referenced by at least one document
     */
    boolean isVersionReferenced(SchemaVersionRef schemaVersionRef);
}
