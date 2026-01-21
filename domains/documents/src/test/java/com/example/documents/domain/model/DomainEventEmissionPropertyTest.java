package com.example.documents.domain.model;

import com.example.documents.domain.event.DerivativeCreated;
import com.example.documents.domain.event.DocumentAdded;
import com.example.documents.domain.event.DocumentSetCreated;
import com.example.documents.domain.event.DocumentVersionAdded;
import com.example.documents.domain.event.DomainEvent;
import com.example.documents.domain.event.SchemaVersionCreated;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for domain event emission.
 * 
 * <p><b>Property 16: Domain Event Emission</b></p>
 * <p>For any state-changing operation (create DocumentSet, add Document, add Version, 
 * create Derivative), the corresponding domain event SHALL be emitted with correct 
 * identifiers and timestamp.</p>
 * 
 * <p><b>Validates: Requirements 9.1, 9.2, 9.3, 9.5, 9.6</b></p>
 */
class DomainEventEmissionPropertyTest {

    @Provide
    Arbitrary<SchemaVersionRef> schemaVersionRefs() {
        return Arbitraries.of(
                SchemaVersionRef.of(
                        new SchemaId(UUID.randomUUID()),
                        new VersionIdentifier("1.0.0")),
                SchemaVersionRef.of(
                        new SchemaId(UUID.randomUUID()),
                        new VersionIdentifier("2.1.0")));
    }

    @Provide
    Arbitrary<DocumentType> documentTypes() {
        return Arbitraries.of(DocumentType.values());
    }

    @Provide
    Arbitrary<Format> formats() {
        return Arbitraries.of(Format.values());
    }

    @Provide
    Arbitrary<TransformationMethod> transformationMethods() {
        return Arbitraries.of(TransformationMethod.values());
    }

    @Provide
    Arbitrary<SchemaFormat> schemaFormats() {
        return Arbitraries.of(SchemaFormat.values());
    }

    @Provide
    Arbitrary<String> userNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> schemaNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(30);
    }

    @Provide
    Arbitrary<VersionIdentifier> versionIdentifiers() {
        return Arbitraries.integers().between(1, 10)
                .flatMap(major -> Arbitraries.integers().between(0, 10)
                        .flatMap(minor -> Arbitraries.integers().between(0, 10)
                                .map(patch -> new VersionIdentifier(major + "." + minor + "." + patch))));
    }

    /**
     * Property 16: Domain Event Emission
     * 
     * <p>Creating a DocumentSet SHALL emit a DocumentSetCreated event with correct identifiers.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 16: DocumentSet creation emits DocumentSetCreated event")
    void documentSetCreationEmitsEvent(@ForAll("userNames") String createdBy) {
        Instant beforeCreation = Instant.now();
        
        DocumentSet documentSet = DocumentSet.create(createdBy, null);
        
        Instant afterCreation = Instant.now();
        
        List<DomainEvent> events = documentSet.domainEvents();
        
        assertThat(events)
                .as("Should emit exactly one event for DocumentSet creation")
                .hasSize(1);
        
        assertThat(events.get(0))
                .as("Event should be DocumentSetCreated")
                .isInstanceOf(DocumentSetCreated.class);
        
        DocumentSetCreated event = (DocumentSetCreated) events.get(0);
        
        assertThat(event.documentSetId())
                .as("Event should contain correct DocumentSetId")
                .isEqualTo(documentSet.id());
        
        assertThat(event.createdBy())
                .as("Event should contain correct createdBy")
                .isEqualTo(createdBy);
        
        assertThat(event.occurredAt())
                .as("Event timestamp should be within creation window")
                .isAfterOrEqualTo(beforeCreation)
                .isBeforeOrEqualTo(afterCreation);
    }

    /**
     * Property 16: Domain Event Emission
     * 
     * <p>Adding a document SHALL emit DocumentAdded and DocumentVersionAdded events.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 16: Adding document emits DocumentAdded and DocumentVersionAdded events")
    void addingDocumentEmitsEvents(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll("userNames") String createdBy) {
        
        DocumentSet documentSet = DocumentSet.create(createdBy, null);
        documentSet.clearDomainEvents(); // Clear creation event
        
        Content content = Content.of(new byte[]{1, 2, 3}, Format.XML);
        Instant beforeAdd = Instant.now();
        
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(content.hash()),
                content.hash(),
                createdBy);
        
        Instant afterAdd = Instant.now();
        
        List<DomainEvent> events = documentSet.domainEvents();
        
        assertThat(events)
                .as("Should emit two events: DocumentAdded and DocumentVersionAdded")
                .hasSize(2);
        
        // Verify DocumentAdded event
        DocumentAdded addedEvent = events.stream()
                .filter(e -> e instanceof DocumentAdded)
                .map(e -> (DocumentAdded) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError("DocumentAdded event not found"));
        
        assertThat(addedEvent.documentSetId())
                .as("DocumentAdded should contain correct DocumentSetId")
                .isEqualTo(documentSet.id());
        
        assertThat(addedEvent.documentId())
                .as("DocumentAdded should contain correct DocumentId")
                .isEqualTo(document.id());
        
        assertThat(addedEvent.type())
                .as("DocumentAdded should contain correct DocumentType")
                .isEqualTo(documentType);
        
        assertThat(addedEvent.schemaRef())
                .as("DocumentAdded should contain correct SchemaVersionRef")
                .isEqualTo(schemaRef);
        
        assertThat(addedEvent.occurredAt())
                .as("DocumentAdded timestamp should be within operation window")
                .isAfterOrEqualTo(beforeAdd)
                .isBeforeOrEqualTo(afterAdd);
        
        // Verify DocumentVersionAdded event for initial version
        DocumentVersionAdded versionEvent = events.stream()
                .filter(e -> e instanceof DocumentVersionAdded)
                .map(e -> (DocumentVersionAdded) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError("DocumentVersionAdded event not found"));
        
        assertThat(versionEvent.documentSetId())
                .as("DocumentVersionAdded should contain correct DocumentSetId")
                .isEqualTo(documentSet.id());
        
        assertThat(versionEvent.documentId())
                .as("DocumentVersionAdded should contain correct DocumentId")
                .isEqualTo(document.id());
        
        assertThat(versionEvent.versionNumber())
                .as("Initial version should be version 1")
                .isEqualTo(1);
        
        assertThat(versionEvent.contentHash())
                .as("DocumentVersionAdded should contain correct ContentHash")
                .isEqualTo(content.hash());
    }

    /**
     * Property 16: Domain Event Emission
     * 
     * <p>Adding a version SHALL emit a DocumentVersionAdded event with correct identifiers.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 16: Adding version emits DocumentVersionAdded event")
    void addingVersionEmitsEvent(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll @IntRange(min = 1, max = 5) int additionalVersions) {
        
        DocumentSet documentSet = DocumentSet.create("user", null);
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user");
        
        documentSet.clearDomainEvents(); // Clear previous events
        
        for (int i = 0; i < additionalVersions; i++) {
            final int versionIndex = i;
            Content newContent = Content.of(("version" + (i + 2)).getBytes(), Format.XML);
            Instant beforeAdd = Instant.now();
            
            DocumentVersion newVersion = documentSet.addVersion(
                    document.id(),
                    ContentRef.of(newContent.hash()),
                    newContent.hash(),
                    "user");
            
            Instant afterAdd = Instant.now();
            
            List<DomainEvent> events = documentSet.domainEvents();
            
            // Find the most recent DocumentVersionAdded event
            DocumentVersionAdded versionEvent = events.stream()
                    .filter(e -> e instanceof DocumentVersionAdded)
                    .map(e -> (DocumentVersionAdded) e)
                    .filter(e -> e.versionId().equals(newVersion.id()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("DocumentVersionAdded event not found for version " + (versionIndex + 2)));
            
            assertThat(versionEvent.documentSetId())
                    .as("Event should contain correct DocumentSetId")
                    .isEqualTo(documentSet.id());
            
            assertThat(versionEvent.documentId())
                    .as("Event should contain correct DocumentId")
                    .isEqualTo(document.id());
            
            assertThat(versionEvent.versionNumber())
                    .as("Event should contain correct version number")
                    .isEqualTo(versionIndex + 2);
            
            assertThat(versionEvent.contentHash())
                    .as("Event should contain correct ContentHash")
                    .isEqualTo(newContent.hash());
            
            assertThat(versionEvent.occurredAt())
                    .as("Event timestamp should be within operation window")
                    .isAfterOrEqualTo(beforeAdd)
                    .isBeforeOrEqualTo(afterAdd);
        }
    }

    /**
     * Property 16: Domain Event Emission
     * 
     * <p>Creating a derivative SHALL emit a DerivativeCreated event with correct identifiers.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 16: Creating derivative emits DerivativeCreated event")
    void creatingDerivativeEmitsEvent(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll("transformationMethods") TransformationMethod method) {
        
        DocumentSet documentSet = DocumentSet.create("user", null);
        Content initialContent = Content.of(new byte[]{1, 2, 3}, Format.XML);
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(initialContent.hash()),
                initialContent.hash(),
                "user");
        
        DocumentVersionId sourceVersionId = document.getCurrentVersion().id();
        documentSet.clearDomainEvents(); // Clear previous events
        
        Content derivativeContent = Content.of("derivative".getBytes(), Format.JSON);
        Instant beforeCreate = Instant.now();
        
        Derivative derivative = documentSet.createDerivative(
                document.id(),
                sourceVersionId,
                Format.JSON,
                ContentRef.of(derivativeContent.hash()),
                derivativeContent.hash(),
                method);
        
        Instant afterCreate = Instant.now();
        
        List<DomainEvent> events = documentSet.domainEvents();
        
        assertThat(events)
                .as("Should emit exactly one DerivativeCreated event")
                .hasSize(1);
        
        assertThat(events.get(0))
                .as("Event should be DerivativeCreated")
                .isInstanceOf(DerivativeCreated.class);
        
        DerivativeCreated event = (DerivativeCreated) events.get(0);
        
        assertThat(event.documentSetId())
                .as("Event should contain correct DocumentSetId")
                .isEqualTo(documentSet.id());
        
        assertThat(event.documentId())
                .as("Event should contain correct DocumentId")
                .isEqualTo(document.id());
        
        assertThat(event.derivativeId())
                .as("Event should contain correct DerivativeId")
                .isEqualTo(derivative.id());
        
        assertThat(event.sourceVersionId())
                .as("Event should contain correct source version ID")
                .isEqualTo(sourceVersionId);
        
        assertThat(event.targetFormat())
                .as("Event should contain correct target format")
                .isEqualTo(Format.JSON);
        
        assertThat(event.occurredAt())
                .as("Event timestamp should be within operation window")
                .isAfterOrEqualTo(beforeCreate)
                .isBeforeOrEqualTo(afterCreate);
    }

    /**
     * Property 16: Domain Event Emission
     * 
     * <p>Adding a schema version SHALL emit a SchemaVersionCreated event.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 16: Adding schema version emits SchemaVersionCreated event")
    void addingSchemaVersionEmitsEvent(
            @ForAll("schemaNames") String schemaName,
            @ForAll("schemaFormats") SchemaFormat schemaFormat,
            @ForAll("versionIdentifiers") VersionIdentifier versionId) {
        
        Schema schema = Schema.create(schemaName, schemaFormat);
        schema.clearDomainEvents(); // Clear any creation events if present
        
        Content definitionContent = Content.of("schema-definition".getBytes(), Format.JSON);
        ContentRef definitionRef = ContentRef.of(definitionContent.hash());
        
        Instant beforeAdd = Instant.now();
        
        SchemaVersion schemaVersion = schema.addVersion(versionId, definitionRef);
        
        Instant afterAdd = Instant.now();
        
        List<DomainEvent> events = schema.domainEvents();
        
        assertThat(events)
                .as("Should emit exactly one SchemaVersionCreated event")
                .hasSize(1);
        
        assertThat(events.get(0))
                .as("Event should be SchemaVersionCreated")
                .isInstanceOf(SchemaVersionCreated.class);
        
        SchemaVersionCreated event = (SchemaVersionCreated) events.get(0);
        
        assertThat(event.schemaId())
                .as("Event should contain correct SchemaId")
                .isEqualTo(schema.id());
        
        assertThat(event.versionId())
                .as("Event should contain correct SchemaVersionId")
                .isEqualTo(schemaVersion.id());
        
        assertThat(event.version())
                .as("Event should contain correct VersionIdentifier")
                .isEqualTo(versionId);
        
        assertThat(event.occurredAt())
                .as("Event timestamp should be within operation window")
                .isAfterOrEqualTo(beforeAdd)
                .isBeforeOrEqualTo(afterAdd);
    }

    /**
     * Property 16: Domain Event Emission
     * 
     * <p>Clearing domain events SHALL remove all collected events.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 16: Clearing events removes all collected events")
    void clearingEventsRemovesAllEvents(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType) {
        
        DocumentSet documentSet = DocumentSet.create("user", null);
        Content content = Content.of(new byte[]{1, 2, 3}, Format.XML);
        documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(content.hash()),
                content.hash(),
                "user");
        
        assertThat(documentSet.domainEvents())
                .as("Should have events before clearing")
                .isNotEmpty();
        
        documentSet.clearDomainEvents();
        
        assertThat(documentSet.domainEvents())
                .as("Should have no events after clearing")
                .isEmpty();
    }

    /**
     * Property 16: Domain Event Emission
     * 
     * <p>All events SHALL have non-null timestamps (Requirement 9.6).</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 16: All events have non-null timestamps")
    void allEventsHaveNonNullTimestamps(
            @ForAll("schemaVersionRefs") SchemaVersionRef schemaRef,
            @ForAll("documentTypes") DocumentType documentType,
            @ForAll("transformationMethods") TransformationMethod method) {
        
        DocumentSet documentSet = DocumentSet.create("user", null);
        Content content = Content.of(new byte[]{1, 2, 3}, Format.XML);
        Document document = documentSet.addDocument(
                documentType,
                schemaRef,
                ContentRef.of(content.hash()),
                content.hash(),
                "user");
        
        Content versionContent = Content.of("version2".getBytes(), Format.XML);
        documentSet.addVersion(
                document.id(),
                ContentRef.of(versionContent.hash()),
                versionContent.hash(),
                "user");
        
        Content derivativeContent = Content.of("derivative".getBytes(), Format.JSON);
        documentSet.createDerivative(
                document.id(),
                document.getCurrentVersion().id(),
                Format.JSON,
                ContentRef.of(derivativeContent.hash()),
                derivativeContent.hash(),
                method);
        
        for (DomainEvent event : documentSet.domainEvents()) {
            assertThat(event.occurredAt())
                    .as("Event %s should have non-null timestamp", event.getClass().getSimpleName())
                    .isNotNull();
        }
    }
}
