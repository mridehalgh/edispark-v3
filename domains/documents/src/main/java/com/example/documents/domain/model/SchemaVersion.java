package com.example.documents.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable version of a schema definition.
 * 
 * <p>Each schema version captures a specific revision of a schema's definition at a point in time.
 * Schema versions are immutable once created, ensuring documents can reliably reference
 * a specific schema definition.</p>
 * 
 * <p>Requirements: 3.3, 3.4, 3.5</p>
 */
public final class SchemaVersion {

    private final SchemaVersionId id;
    private final VersionIdentifier versionIdentifier;
    private final ContentRef definitionRef;
    private final Instant createdAt;
    private final boolean deprecated;

    private SchemaVersion(
            SchemaVersionId id,
            VersionIdentifier versionIdentifier,
            ContentRef definitionRef,
            Instant createdAt,
            boolean deprecated) {
        this.id = Objects.requireNonNull(id, "Schema version ID cannot be null");
        this.versionIdentifier = Objects.requireNonNull(versionIdentifier, "Version identifier cannot be null");
        this.definitionRef = Objects.requireNonNull(definitionRef, "Definition reference cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        this.deprecated = deprecated;
    }

    /**
     * Creates a new schema version.
     */
    public static SchemaVersion create(
            VersionIdentifier versionIdentifier,
            ContentRef definitionRef) {
        return new SchemaVersion(
                SchemaVersionId.generate(),
                versionIdentifier,
                definitionRef,
                Instant.now(),
                false);
    }

    /**
     * Creates a SchemaVersion with all fields specified (for reconstruction from persistence).
     */
    public static SchemaVersion reconstitute(
            SchemaVersionId id,
            VersionIdentifier versionIdentifier,
            ContentRef definitionRef,
            Instant createdAt,
            boolean deprecated) {
        return new SchemaVersion(id, versionIdentifier, definitionRef, createdAt, deprecated);
    }

    public SchemaVersionId id() {
        return id;
    }

    public VersionIdentifier versionIdentifier() {
        return versionIdentifier;
    }

    public ContentRef definitionRef() {
        return definitionRef;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaVersion that = (SchemaVersion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SchemaVersion[id=" + id + ", version=" + versionIdentifier + ", deprecated=" + deprecated + "]";
    }
}
