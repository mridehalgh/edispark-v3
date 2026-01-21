package com.example.documents.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SchemaVersion entity behaviour.
 * 
 * <p>Tests focus on domain behaviour rather than getters/setters.</p>
 */
@DisplayName("SchemaVersion entity")
class SchemaVersionTest {

    private ContentRef createContentRef(String content) {
        return ContentRef.of(Content.of(content.getBytes(), Format.XML).hash());
    }

    @Nested
    @DisplayName("when creating a schema version")
    class Creation {

        @Test
        @DisplayName("creates version with unique ID")
        void createsVersionWithUniqueId() {
            ContentRef definitionRef = createContentRef("<schema/>");
            VersionIdentifier versionId = VersionIdentifier.of("1.0.0");

            SchemaVersion version1 = SchemaVersion.create(versionId, definitionRef);
            SchemaVersion version2 = SchemaVersion.create(versionId, definitionRef);

            assertThat(version1.id()).isNotEqualTo(version2.id());
        }

        @Test
        @DisplayName("creates version with current timestamp")
        void createsVersionWithCurrentTimestamp() {
            Instant before = Instant.now();
            SchemaVersion version = SchemaVersion.create(
                    VersionIdentifier.of("1.0.0"),
                    createContentRef("<schema/>"));
            Instant after = Instant.now();

            assertThat(version.createdAt())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }

        @Test
        @DisplayName("creates version as not deprecated by default")
        void createsVersionAsNotDeprecatedByDefault() {
            SchemaVersion version = SchemaVersion.create(
                    VersionIdentifier.of("1.0.0"),
                    createContentRef("<schema/>"));

            assertThat(version.isDeprecated()).isFalse();
        }

        @Test
        @DisplayName("rejects null version identifier")
        void rejectsNullVersionIdentifier() {
            assertThatThrownBy(() -> 
                    SchemaVersion.create(null, createContentRef("<schema/>")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Version identifier");
        }

        @Test
        @DisplayName("rejects null definition reference")
        void rejectsNullDefinitionReference() {
            assertThatThrownBy(() -> 
                    SchemaVersion.create(VersionIdentifier.of("1.0.0"), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Definition reference");
        }
    }

    @Nested
    @DisplayName("when reconstituting from persistence")
    class Reconstitution {

        @Test
        @DisplayName("reconstitutes with all fields")
        void reconstitutesWithAllFields() {
            SchemaVersionId id = SchemaVersionId.generate();
            VersionIdentifier versionId = VersionIdentifier.of("2.1.0");
            ContentRef definitionRef = createContentRef("<schema version='2.1.0'/>");
            Instant createdAt = Instant.parse("2024-01-15T10:30:00Z");

            SchemaVersion version = SchemaVersion.reconstitute(
                    id, versionId, definitionRef, createdAt, false);

            assertThat(version.id()).isEqualTo(id);
            assertThat(version.versionIdentifier()).isEqualTo(versionId);
            assertThat(version.definitionRef()).isEqualTo(definitionRef);
            assertThat(version.createdAt()).isEqualTo(createdAt);
            assertThat(version.isDeprecated()).isFalse();
        }

        @Test
        @DisplayName("reconstitutes deprecated version")
        void reconstitutesDeprecatedVersion() {
            SchemaVersionId id = SchemaVersionId.generate();
            VersionIdentifier versionId = VersionIdentifier.of("1.0.0");
            ContentRef definitionRef = createContentRef("<old-schema/>");
            Instant createdAt = Instant.parse("2020-01-01T00:00:00Z");

            SchemaVersion version = SchemaVersion.reconstitute(
                    id, versionId, definitionRef, createdAt, true);

            assertThat(version.isDeprecated()).isTrue();
        }

        @Test
        @DisplayName("rejects null ID in reconstitution")
        void rejectsNullIdInReconstitution() {
            assertThatThrownBy(() -> 
                    SchemaVersion.reconstitute(
                            null,
                            VersionIdentifier.of("1.0.0"),
                            createContentRef("<schema/>"),
                            Instant.now(),
                            false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ID");
        }

        @Test
        @DisplayName("rejects null timestamp in reconstitution")
        void rejectsNullTimestampInReconstitution() {
            assertThatThrownBy(() -> 
                    SchemaVersion.reconstitute(
                            SchemaVersionId.generate(),
                            VersionIdentifier.of("1.0.0"),
                            createContentRef("<schema/>"),
                            null,
                            false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("timestamp");
        }
    }

    @Nested
    @DisplayName("equality and identity")
    class EqualityAndIdentity {

        @Test
        @DisplayName("versions with same ID are equal")
        void versionsWithSameIdAreEqual() {
            SchemaVersionId id = SchemaVersionId.generate();
            ContentRef ref1 = createContentRef("<schema1/>");
            ContentRef ref2 = createContentRef("<schema2/>");

            SchemaVersion version1 = SchemaVersion.reconstitute(
                    id, VersionIdentifier.of("1.0.0"), ref1, Instant.now(), false);
            SchemaVersion version2 = SchemaVersion.reconstitute(
                    id, VersionIdentifier.of("2.0.0"), ref2, Instant.now(), true);

            assertThat(version1).isEqualTo(version2);
            assertThat(version1.hashCode()).isEqualTo(version2.hashCode());
        }

        @Test
        @DisplayName("versions with different IDs are not equal")
        void versionsWithDifferentIdsAreNotEqual() {
            ContentRef definitionRef = createContentRef("<schema/>");
            VersionIdentifier versionId = VersionIdentifier.of("1.0.0");

            SchemaVersion version1 = SchemaVersion.create(versionId, definitionRef);
            SchemaVersion version2 = SchemaVersion.create(versionId, definitionRef);

            assertThat(version1).isNotEqualTo(version2);
        }

        @Test
        @DisplayName("version is not equal to null")
        void versionIsNotEqualToNull() {
            SchemaVersion version = SchemaVersion.create(
                    VersionIdentifier.of("1.0.0"),
                    createContentRef("<schema/>"));

            assertThat(version).isNotEqualTo(null);
        }

        @Test
        @DisplayName("version is not equal to different type")
        void versionIsNotEqualToDifferentType() {
            SchemaVersion version = SchemaVersion.create(
                    VersionIdentifier.of("1.0.0"),
                    createContentRef("<schema/>"));

            assertThat(version).isNotEqualTo("not a schema version");
        }
    }

    @Nested
    @DisplayName("toString behaviour")
    class ToStringBehaviour {

        @Test
        @DisplayName("toString includes version identifier")
        void toStringIncludesVersionIdentifier() {
            SchemaVersion version = SchemaVersion.create(
                    VersionIdentifier.of("2.1.0"),
                    createContentRef("<schema/>"));

            assertThat(version.toString()).contains("2.1.0");
        }

        @Test
        @DisplayName("toString indicates deprecated status")
        void toStringIndicatesDeprecatedStatus() {
            SchemaVersion deprecated = SchemaVersion.reconstitute(
                    SchemaVersionId.generate(),
                    VersionIdentifier.of("1.0.0"),
                    createContentRef("<schema/>"),
                    Instant.now(),
                    true);

            assertThat(deprecated.toString()).contains("deprecated=true");
        }
    }
}
