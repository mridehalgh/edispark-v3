package com.example.documents.domain.model;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for schema version immutability.
 * 
 * <p><b>Property 8: Schema Version Immutability</b></p>
 * <p>For any SchemaVersion, once created, its definition content and version identifier 
 * SHALL remain unchanged.</p>
 * 
 * <p><b>Validates: Requirements 3.3</b></p>
 */
class SchemaVersionImmutabilityPropertyTest {

    @Provide
    Arbitrary<SchemaFormat> schemaFormats() {
        return Arbitraries.of(SchemaFormat.values());
    }

    @Provide
    Arbitrary<String> schemaNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "Schema-" + s);
    }

    @Provide
    Arbitrary<String> versionStrings() {
        return Arbitraries.integers().between(1, 10)
                .flatMap(major -> Arbitraries.integers().between(0, 10)
                        .flatMap(minor -> Arbitraries.integers().between(0, 10)
                                .map(patch -> major + "." + minor + "." + patch)));
    }

    /**
     * Property 8: Schema Version Immutability
     * 
     * <p>Schema version definition reference remains unchanged after adding more versions.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 8: Schema version definition remains unchanged after adding versions")
    void schemaVersionDefinitionRemainsUnchangedAfterAddingVersions(
            @ForAll("schemaFormats") SchemaFormat format,
            @ForAll("schemaNames") String schemaName,
            @ForAll @IntRange(min = 1, max = 5) int additionalVersions) {
        
        Schema schema = Schema.create(schemaName, format);
        
        // Add initial version
        Content initialDefinition = Content.of(("<schema version='1.0.0'/>").getBytes(), Format.XML);
        VersionIdentifier initialVersionId = VersionIdentifier.of("1.0.0");
        SchemaVersion initialVersion = schema.addVersion(
                initialVersionId,
                ContentRef.of(initialDefinition.hash()));
        
        // Capture original properties
        ContentRef originalDefinitionRef = initialVersion.definitionRef();
        VersionIdentifier originalVersionIdentifier = initialVersion.versionIdentifier();
        Instant originalCreatedAt = initialVersion.createdAt();
        SchemaVersionId originalId = initialVersion.id();

        // Add multiple new versions
        for (int i = 0; i < additionalVersions; i++) {
            int major = 1;
            int minor = i + 1;
            String versionStr = major + "." + minor + ".0";
            Content newDefinition = Content.of(("<schema version='" + versionStr + "'/>").getBytes(), Format.XML);
            schema.addVersion(
                    VersionIdentifier.of(versionStr),
                    ContentRef.of(newDefinition.hash()));
        }

        // Verify original version properties remain unchanged
        SchemaVersion retrievedVersion = schema.getVersion(initialVersionId).orElseThrow();
        
        assertThat(retrievedVersion.id())
                .as("Schema version ID should remain unchanged")
                .isEqualTo(originalId);
        assertThat(retrievedVersion.definitionRef())
                .as("Definition reference should remain unchanged")
                .isEqualTo(originalDefinitionRef);
        assertThat(retrievedVersion.versionIdentifier())
                .as("Version identifier should remain unchanged")
                .isEqualTo(originalVersionIdentifier);
        assertThat(retrievedVersion.createdAt())
                .as("Creation timestamp should remain unchanged")
                .isEqualTo(originalCreatedAt);
    }

    /**
     * Property 8: Schema Version Immutability
     * 
     * <p>All historical schema versions remain immutable after subsequent operations.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 8: All historical schema versions remain immutable")
    void allHistoricalSchemaVersionsRemainImmutable(
            @ForAll("schemaFormats") SchemaFormat format,
            @ForAll("schemaNames") String schemaName,
            @ForAll @IntRange(min = 2, max = 6) int totalVersions) {
        
        Schema schema = Schema.create(schemaName, format);
        
        // Create versions and capture their properties
        ContentRef[] originalDefinitionRefs = new ContentRef[totalVersions];
        VersionIdentifier[] originalVersionIdentifiers = new VersionIdentifier[totalVersions];
        Instant[] originalTimestamps = new Instant[totalVersions];
        SchemaVersionId[] originalIds = new SchemaVersionId[totalVersions];

        for (int i = 0; i < totalVersions; i++) {
            String versionStr = (i + 1) + ".0.0";
            Content definition = Content.of(("<schema version='" + versionStr + "'/>").getBytes(), Format.XML);
            VersionIdentifier versionId = VersionIdentifier.of(versionStr);
            
            SchemaVersion version = schema.addVersion(versionId, ContentRef.of(definition.hash()));
            
            originalIds[i] = version.id();
            originalDefinitionRefs[i] = version.definitionRef();
            originalVersionIdentifiers[i] = version.versionIdentifier();
            originalTimestamps[i] = version.createdAt();
        }

        // Verify all versions remain immutable
        for (int i = 0; i < totalVersions; i++) {
            SchemaVersion version = schema.getVersion(originalVersionIdentifiers[i]).orElseThrow();
            
            assertThat(version.id())
                    .as("Version %d ID should remain unchanged", i + 1)
                    .isEqualTo(originalIds[i]);
            assertThat(version.definitionRef())
                    .as("Version %d definition reference should remain unchanged", i + 1)
                    .isEqualTo(originalDefinitionRefs[i]);
            assertThat(version.versionIdentifier())
                    .as("Version %d identifier should remain unchanged", i + 1)
                    .isEqualTo(originalVersionIdentifiers[i]);
            assertThat(version.createdAt())
                    .as("Version %d timestamp should remain unchanged", i + 1)
                    .isEqualTo(originalTimestamps[i]);
        }
    }

    /**
     * Property 8: Schema Version Immutability
     * 
     * <p>Schema version immutability is preserved across multiple schemas.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 8: Schema version immutability across multiple schemas")
    void schemaVersionImmutabilityAcrossMultipleSchemas(
            @ForAll @IntRange(min = 2, max = 4) int schemaCount,
            @ForAll @IntRange(min = 1, max = 3) int versionsPerSchema) {
        
        Schema[] schemas = new Schema[schemaCount];
        ContentRef[][] originalDefinitionRefs = new ContentRef[schemaCount][versionsPerSchema];
        VersionIdentifier[][] originalVersionIdentifiers = new VersionIdentifier[schemaCount][versionsPerSchema];
        
        // Create schemas and versions, capturing properties
        for (int s = 0; s < schemaCount; s++) {
            SchemaFormat format = SchemaFormat.values()[s % SchemaFormat.values().length];
            schemas[s] = Schema.create("Schema-" + s, format);
            
            for (int v = 0; v < versionsPerSchema; v++) {
                String versionStr = (v + 1) + ".0.0";
                Content definition = Content.of(
                        ("<schema name='Schema-" + s + "' version='" + versionStr + "'/>").getBytes(),
                        Format.XML);
                VersionIdentifier versionId = VersionIdentifier.of(versionStr);
                
                SchemaVersion version = schemas[s].addVersion(versionId, ContentRef.of(definition.hash()));
                
                originalDefinitionRefs[s][v] = version.definitionRef();
                originalVersionIdentifiers[s][v] = version.versionIdentifier();
            }
        }

        // Verify all versions across all schemas remain immutable
        for (int s = 0; s < schemaCount; s++) {
            for (int v = 0; v < versionsPerSchema; v++) {
                SchemaVersion version = schemas[s].getVersion(originalVersionIdentifiers[s][v]).orElseThrow();
                
                assertThat(version.definitionRef())
                        .as("Schema %d, Version %d definition reference should remain unchanged", s, v + 1)
                        .isEqualTo(originalDefinitionRefs[s][v]);
                assertThat(version.versionIdentifier())
                        .as("Schema %d, Version %d identifier should remain unchanged", s, v + 1)
                        .isEqualTo(originalVersionIdentifiers[s][v]);
            }
        }
    }

    /**
     * Property 8: Schema Version Immutability
     * 
     * <p>Schema version deprecated status does not affect other immutable properties.</p>
     */
    @Property(tries = 50)
    @Label("Feature: documents-domain, Property 8: Reconstituted deprecated versions preserve immutable properties")
    void reconstitutedDeprecatedVersionsPreserveImmutableProperties(
            @ForAll("schemaFormats") SchemaFormat format,
            @ForAll("schemaNames") String schemaName,
            @ForAll("versionStrings") String versionStr) {
        
        Content definition = Content.of(("<schema version='" + versionStr + "'/>").getBytes(), Format.XML);
        VersionIdentifier versionId = VersionIdentifier.of(versionStr);
        ContentRef definitionRef = ContentRef.of(definition.hash());
        Instant createdAt = Instant.now();
        SchemaVersionId id = SchemaVersionId.generate();
        
        // Create a non-deprecated version
        SchemaVersion nonDeprecated = SchemaVersion.reconstitute(
                id, versionId, definitionRef, createdAt, false);
        
        // Create a deprecated version with same properties
        SchemaVersion deprecated = SchemaVersion.reconstitute(
                id, versionId, definitionRef, createdAt, true);
        
        // Verify immutable properties are the same regardless of deprecated status
        assertThat(deprecated.id())
                .as("ID should be preserved")
                .isEqualTo(nonDeprecated.id());
        assertThat(deprecated.versionIdentifier())
                .as("Version identifier should be preserved")
                .isEqualTo(nonDeprecated.versionIdentifier());
        assertThat(deprecated.definitionRef())
                .as("Definition reference should be preserved")
                .isEqualTo(nonDeprecated.definitionRef());
        assertThat(deprecated.createdAt())
                .as("Creation timestamp should be preserved")
                .isEqualTo(nonDeprecated.createdAt());
        
        // Only deprecated status differs
        assertThat(deprecated.isDeprecated()).isTrue();
        assertThat(nonDeprecated.isDeprecated()).isFalse();
    }
}
