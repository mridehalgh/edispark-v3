package com.example.documents.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Schema aggregate root behaviour.
 * 
 * <p>Tests focus on domain behaviour rather than getters/setters.</p>
 */
@DisplayName("Schema aggregate")
class SchemaTest {

    private ContentRef createContentRef(String content) {
        return ContentRef.of(Content.of(content.getBytes(), Format.XML).hash());
    }

    @Nested
    @DisplayName("when creating a schema")
    class Creation {

        @Test
        @DisplayName("creates schema with unique ID")
        void createsSchemaWithUniqueId() {
            Schema schema1 = Schema.create("Invoice Schema", SchemaFormat.XSD);
            Schema schema2 = Schema.create("Invoice Schema", SchemaFormat.XSD);

            assertThat(schema1.id()).isNotEqualTo(schema2.id());
        }

        @Test
        @DisplayName("creates schema with no versions initially")
        void createsSchemaWithNoVersions() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);

            assertThat(schema.hasVersions()).isFalse();
            assertThat(schema.versionCount()).isZero();
            assertThat(schema.versions()).isEmpty();
        }

        @Test
        @DisplayName("rejects null name")
        void rejectsNullName() {
            assertThatThrownBy(() -> Schema.create(null, SchemaFormat.XSD))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("rejects blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Schema.create("   ", SchemaFormat.XSD))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("rejects null format")
        void rejectsNullFormat() {
            assertThatThrownBy(() -> Schema.create("Invoice Schema", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("format");
        }
    }

    @Nested
    @DisplayName("when adding versions")
    class AddingVersions {

        @Test
        @DisplayName("adds first version successfully")
        void addsFirstVersionSuccessfully() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);
            ContentRef definitionRef = createContentRef("<schema/>");

            SchemaVersion version = schema.addVersion(
                    VersionIdentifier.of("1.0.0"),
                    definitionRef);

            assertThat(schema.hasVersions()).isTrue();
            assertThat(schema.versionCount()).isEqualTo(1);
            assertThat(version.versionIdentifier().value()).isEqualTo("1.0.0");
            assertThat(version.definitionRef()).isEqualTo(definitionRef);
        }

        @Test
        @DisplayName("adds multiple versions with different identifiers")
        void addsMultipleVersionsWithDifferentIdentifiers() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);

            schema.addVersion(VersionIdentifier.of("1.0.0"), createContentRef("<v1/>"));
            schema.addVersion(VersionIdentifier.of("1.1.0"), createContentRef("<v1.1/>"));
            schema.addVersion(VersionIdentifier.of("2.0.0"), createContentRef("<v2/>"));

            assertThat(schema.versionCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("rejects duplicate version identifier")
        void rejectsDuplicateVersionIdentifier() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);
            schema.addVersion(VersionIdentifier.of("1.0.0"), createContentRef("<v1/>"));

            assertThatThrownBy(() -> 
                    schema.addVersion(VersionIdentifier.of("1.0.0"), createContentRef("<v1-dup/>")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1.0.0")
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("rejects null version identifier")
        void rejectsNullVersionIdentifier() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);

            assertThatThrownBy(() -> 
                    schema.addVersion(null, createContentRef("<schema/>")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Version identifier");
        }

        @Test
        @DisplayName("rejects null definition reference")
        void rejectsNullDefinitionReference() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);

            assertThatThrownBy(() -> 
                    schema.addVersion(VersionIdentifier.of("1.0.0"), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Definition reference");
        }

        @Test
        @DisplayName("returns the newly created version")
        void returnsNewlyCreatedVersion() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);
            ContentRef definitionRef = createContentRef("<schema/>");

            SchemaVersion version = schema.addVersion(
                    VersionIdentifier.of("1.0.0"),
                    definitionRef);

            assertThat(version).isNotNull();
            assertThat(version.id()).isNotNull();
            assertThat(version.createdAt()).isNotNull();
            assertThat(version.isDeprecated()).isFalse();
        }
    }

    @Nested
    @DisplayName("when retrieving versions")
    class RetrievingVersions {

        @Test
        @DisplayName("retrieves version by identifier")
        void retrievesVersionByIdentifier() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);
            ContentRef definitionRef = createContentRef("<v1/>");
            schema.addVersion(VersionIdentifier.of("1.0.0"), definitionRef);
            schema.addVersion(VersionIdentifier.of("2.0.0"), createContentRef("<v2/>"));

            var retrieved = schema.getVersion(VersionIdentifier.of("1.0.0"));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().versionIdentifier().value()).isEqualTo("1.0.0");
            assertThat(retrieved.get().definitionRef()).isEqualTo(definitionRef);
        }

        @Test
        @DisplayName("returns empty for non-existent version")
        void returnsEmptyForNonExistentVersion() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);
            schema.addVersion(VersionIdentifier.of("1.0.0"), createContentRef("<v1/>"));

            var retrieved = schema.getVersion(VersionIdentifier.of("9.9.9"));

            assertThat(retrieved).isEmpty();
        }

        @Test
        @DisplayName("rejects null version identifier in getVersion")
        void rejectsNullVersionIdentifierInGetVersion() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);

            assertThatThrownBy(() -> schema.getVersion(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("returns empty latest version for schema with no versions")
        void returnsEmptyLatestVersionForSchemaWithNoVersions() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);

            assertThat(schema.getLatestVersion()).isEmpty();
        }

        @Test
        @DisplayName("returns latest version based on creation time")
        void returnsLatestVersionBasedOnCreationTime() throws InterruptedException {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);
            schema.addVersion(VersionIdentifier.of("1.0.0"), createContentRef("<v1/>"));
            Thread.sleep(10); // Ensure different timestamps
            schema.addVersion(VersionIdentifier.of("2.0.0"), createContentRef("<v2/>"));
            Thread.sleep(10);
            schema.addVersion(VersionIdentifier.of("1.5.0"), createContentRef("<v1.5/>"));

            var latest = schema.getLatestVersion();

            assertThat(latest).isPresent();
            assertThat(latest.get().versionIdentifier().value()).isEqualTo("1.5.0");
        }

        @Test
        @DisplayName("versions list is unmodifiable")
        void versionsListIsUnmodifiable() {
            Schema schema = Schema.create("Invoice Schema", SchemaFormat.XSD);
            schema.addVersion(VersionIdentifier.of("1.0.0"), createContentRef("<v1/>"));

            List<SchemaVersion> versions = schema.versions();

            assertThatThrownBy(() -> versions.add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("when reconstituting from persistence")
    class Reconstitution {

        @Test
        @DisplayName("reconstitutes schema with existing versions")
        void reconstitutesSchemaWithExistingVersions() {
            SchemaId id = SchemaId.generate();
            SchemaVersionId versionId = SchemaVersionId.generate();
            ContentRef definitionRef = createContentRef("<schema/>");
            Instant createdAt = Instant.now();

            SchemaVersion version = SchemaVersion.reconstitute(
                    versionId,
                    VersionIdentifier.of("1.0.0"),
                    definitionRef,
                    createdAt,
                    false);

            Schema schema = Schema.reconstitute(
                    id,
                    "Invoice Schema",
                    SchemaFormat.XSD,
                    List.of(version));

            assertThat(schema.id()).isEqualTo(id);
            assertThat(schema.name()).isEqualTo("Invoice Schema");
            assertThat(schema.format()).isEqualTo(SchemaFormat.XSD);
            assertThat(schema.versionCount()).isEqualTo(1);
            assertThat(schema.getVersion(VersionIdentifier.of("1.0.0"))).isPresent();
        }

        @Test
        @DisplayName("reconstituted schema can add new versions")
        void reconstitutedSchemaCanAddNewVersions() {
            SchemaId id = SchemaId.generate();
            Schema schema = Schema.reconstitute(id, "Invoice Schema", SchemaFormat.XSD, List.of());

            schema.addVersion(VersionIdentifier.of("1.0.0"), createContentRef("<v1/>"));

            assertThat(schema.versionCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("equality and identity")
    class EqualityAndIdentity {

        @Test
        @DisplayName("schemas with same ID are equal")
        void schemasWithSameIdAreEqual() {
            SchemaId id = SchemaId.generate();
            Schema schema1 = Schema.reconstitute(id, "Schema 1", SchemaFormat.XSD, List.of());
            Schema schema2 = Schema.reconstitute(id, "Schema 2", SchemaFormat.JSON_SCHEMA, List.of());

            assertThat(schema1).isEqualTo(schema2);
            assertThat(schema1.hashCode()).isEqualTo(schema2.hashCode());
        }

        @Test
        @DisplayName("schemas with different IDs are not equal")
        void schemasWithDifferentIdsAreNotEqual() {
            Schema schema1 = Schema.create("Invoice Schema", SchemaFormat.XSD);
            Schema schema2 = Schema.create("Invoice Schema", SchemaFormat.XSD);

            assertThat(schema1).isNotEqualTo(schema2);
        }
    }
}
