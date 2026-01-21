package com.example.documents.domain.event;

import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionId;
import com.example.documents.domain.model.VersionIdentifier;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted when a new schema version is created.
 * 
 * <p>Requirements: 9.5, 9.6</p>
 *
 * @param schemaId the identifier of the schema
 * @param versionId the identifier of the new schema version
 * @param version the version identifier (e.g., "2.1.0")
 * @param occurredAt the timestamp when the event occurred
 */
public record SchemaVersionCreated(
        SchemaId schemaId,
        SchemaVersionId versionId,
        VersionIdentifier version,
        Instant occurredAt
) implements DomainEvent {

    public SchemaVersionCreated {
        Objects.requireNonNull(schemaId, "SchemaId cannot be null");
        Objects.requireNonNull(versionId, "VersionId cannot be null");
        Objects.requireNonNull(version, "Version cannot be null");
        Objects.requireNonNull(occurredAt, "OccurredAt cannot be null");
    }

    /**
     * Creates a new SchemaVersionCreated event with the current timestamp.
     */
    public static SchemaVersionCreated now(
            SchemaId schemaId,
            SchemaVersionId versionId,
            VersionIdentifier version) {
        return new SchemaVersionCreated(schemaId, versionId, version, Instant.now());
    }
}
