package com.example.documents.domain.model;

import com.example.documents.domain.event.DomainEvent;
import com.example.documents.domain.event.SchemaVersionCreated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for schema definitions.
 * 
 * <p>A Schema represents a formal definition of document structure and validation rules.
 * It maintains a list of immutable schema versions, allowing documents to reference
 * specific versions for validation.</p>
 * 
 * <p>Domain events are emitted for significant state changes:
 * <ul>
 *   <li>{@link SchemaVersionCreated} - when a new schema version is added</li>
 * </ul>
 * 
 * <p>Requirements: 3.1, 3.2, 3.3</p>
 */
public final class Schema {

    private final SchemaId id;
    private final String name;
    private final SchemaFormat format;
    private final List<SchemaVersion> versions;
    private final List<DomainEvent> domainEvents;

    private Schema(
            SchemaId id,
            String name,
            SchemaFormat format,
            List<SchemaVersion> versions) {
        this.id = Objects.requireNonNull(id, "Schema ID cannot be null");
        this.name = Objects.requireNonNull(name, "Schema name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be blank");
        }
        this.format = Objects.requireNonNull(format, "Schema format cannot be null");
        this.versions = new ArrayList<>(Objects.requireNonNull(versions, "Versions list cannot be null"));
        this.domainEvents = new ArrayList<>();
    }

    /**
     * Creates a new Schema with no versions.
     */
    public static Schema create(String name, SchemaFormat format) {
        return new Schema(
                SchemaId.generate(),
                name,
                format,
                new ArrayList<>());
    }

    /**
     * Creates a Schema with all fields specified (for reconstruction from persistence).
     * 
     * <p>Note: Reconstituted aggregates do not emit events for past state changes.</p>
     */
    public static Schema reconstitute(
            SchemaId id,
            String name,
            SchemaFormat format,
            List<SchemaVersion> versions) {
        return new Schema(id, name, format, versions);
    }

    /**
     * Adds a new version to this schema.
     * 
     * @param versionIdentifier the version identifier (e.g., "2.1.0")
     * @param definitionRef reference to the schema definition content
     * @return the newly created SchemaVersion
     * @throws IllegalArgumentException if a version with the same identifier already exists
     */
    public SchemaVersion addVersion(VersionIdentifier versionIdentifier, ContentRef definitionRef) {
        Objects.requireNonNull(versionIdentifier, "Version identifier cannot be null");
        Objects.requireNonNull(definitionRef, "Definition reference cannot be null");

        // Check for duplicate version identifier
        boolean exists = versions.stream()
                .anyMatch(v -> v.versionIdentifier().equals(versionIdentifier));
        if (exists) {
            throw new IllegalArgumentException(
                    "Schema version " + versionIdentifier + " already exists for schema " + name);
        }

        SchemaVersion newVersion = SchemaVersion.create(versionIdentifier, definitionRef);
        versions.add(newVersion);
        
        // Emit SchemaVersionCreated event
        registerEvent(SchemaVersionCreated.now(this.id, newVersion.id(), versionIdentifier));
        
        return newVersion;
    }

    /**
     * Gets a specific version by its identifier.
     * 
     * @param versionIdentifier the version identifier to find
     * @return the schema version if found
     */
    public Optional<SchemaVersion> getVersion(VersionIdentifier versionIdentifier) {
        Objects.requireNonNull(versionIdentifier, "Version identifier cannot be null");
        return versions.stream()
                .filter(v -> v.versionIdentifier().equals(versionIdentifier))
                .findFirst();
    }

    /**
     * Gets the latest (most recently created) version.
     * 
     * @return the latest schema version, or empty if no versions exist
     */
    public Optional<SchemaVersion> getLatestVersion() {
        return versions.stream()
                .max(Comparator.comparing(SchemaVersion::createdAt));
    }

    /**
     * Gets all versions of this schema.
     * 
     * @return an unmodifiable list of all versions
     */
    public List<SchemaVersion> versions() {
        return Collections.unmodifiableList(versions);
    }

    /**
     * Checks if this schema has any versions.
     */
    public boolean hasVersions() {
        return !versions.isEmpty();
    }

    /**
     * Gets the number of versions in this schema.
     */
    public int versionCount() {
        return versions.size();
    }

    /**
     * Returns all domain events that have been emitted since the last clear.
     * 
     * @return an unmodifiable list of domain events
     */
    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears all collected domain events.
     * 
     * <p>This should be called after events have been published to avoid re-publishing.</p>
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    public SchemaId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public SchemaFormat format() {
        return format;
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schema schema = (Schema) o;
        return Objects.equals(id, schema.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Schema[id=" + id + ", name=" + name + ", format=" + format + ", versions=" + versions.size() + "]";
    }
}
