package com.example.documents.application.handler;

import com.example.documents.application.command.AddSchemaVersionCommand;
import com.example.documents.application.command.CreateSchemaCommand;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersion;
import com.example.documents.domain.model.VersionIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SchemaCommandHandler")
class SchemaCommandHandlerTest {

    private InMemorySchemaRepository schemaRepository;
    private InMemoryContentStore contentStore;
    private SchemaCommandHandler handler;

    @BeforeEach
    void setUp() {
        schemaRepository = new InMemorySchemaRepository();
        contentStore = new InMemoryContentStore();
        handler = new SchemaCommandHandler(schemaRepository, contentStore);
    }

    @Nested
    @DisplayName("CreateSchemaCommand")
    class CreateSchemaCommandTests {

        @Test
        @DisplayName("creates schema with given name and format")
        void createsSchemaWithNameAndFormat() {
            CreateSchemaCommand command = CreateSchemaCommand.of("UBL Invoice", SchemaFormat.JSON_SCHEMA);

            Schema result = handler.handle(command);

            assertThat(result).isNotNull();
            assertThat(result.id()).isNotNull();
            assertThat(result.name()).isEqualTo("UBL Invoice");
            assertThat(result.format()).isEqualTo(SchemaFormat.JSON_SCHEMA);
            assertThat(result.hasVersions()).isFalse();
            assertThat(schemaRepository.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("creates schema with XSD format")
        void createsSchemaWithXsdFormat() {
            CreateSchemaCommand command = CreateSchemaCommand.of("XML Schema", SchemaFormat.XSD);

            Schema result = handler.handle(command);

            assertThat(result.format()).isEqualTo(SchemaFormat.XSD);
        }
    }

    @Nested
    @DisplayName("AddSchemaVersionCommand")
    class AddSchemaVersionCommandTests {

        private Schema existingSchema;

        @BeforeEach
        void setUp() {
            existingSchema = Schema.create("Test Schema", SchemaFormat.JSON_SCHEMA);
            schemaRepository.save(existingSchema);
        }

        @Test
        @DisplayName("adds version to existing schema")
        void addsVersionToExistingSchema() {
            Content definition = Content.of(
                    "{\"type\": \"object\"}".getBytes(StandardCharsets.UTF_8),
                    Format.JSON);

            AddSchemaVersionCommand command = AddSchemaVersionCommand.of(
                    existingSchema.id(),
                    VersionIdentifier.of("1.0.0"),
                    definition);

            SchemaVersion result = handler.handle(command);

            assertThat(result).isNotNull();
            assertThat(result.versionIdentifier()).isEqualTo(VersionIdentifier.of("1.0.0"));
            assertThat(existingSchema.versionCount()).isEqualTo(1);
            assertThat(contentStore.exists(definition.hash())).isTrue();
        }

        @Test
        @DisplayName("adds multiple versions to schema")
        void addsMultipleVersions() {
            Content definition1 = Content.of("{\"version\": 1}".getBytes(), Format.JSON);
            Content definition2 = Content.of("{\"version\": 2}".getBytes(), Format.JSON);

            handler.handle(AddSchemaVersionCommand.of(
                    existingSchema.id(),
                    VersionIdentifier.of("1.0.0"),
                    definition1));

            handler.handle(AddSchemaVersionCommand.of(
                    existingSchema.id(),
                    VersionIdentifier.of("2.0.0"),
                    definition2));

            assertThat(existingSchema.versionCount()).isEqualTo(2);
            assertThat(existingSchema.getVersion(VersionIdentifier.of("1.0.0"))).isPresent();
            assertThat(existingSchema.getVersion(VersionIdentifier.of("2.0.0"))).isPresent();
        }

        @Test
        @DisplayName("throws SchemaNotFoundException when schema does not exist")
        void throwsWhenSchemaNotFound() {
            SchemaId nonExistentId = SchemaId.generate();

            Content definition = Content.of("{}".getBytes(), Format.JSON);
            AddSchemaVersionCommand command = AddSchemaVersionCommand.of(
                    nonExistentId,
                    VersionIdentifier.of("1.0.0"),
                    definition);

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(SchemaNotFoundException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when version already exists")
        void throwsWhenVersionAlreadyExists() {
            Content definition = Content.of("{}".getBytes(), Format.JSON);
            AddSchemaVersionCommand command = AddSchemaVersionCommand.of(
                    existingSchema.id(),
                    VersionIdentifier.of("1.0.0"),
                    definition);

            handler.handle(command);

            Content definition2 = Content.of("{\"new\": true}".getBytes(), Format.JSON);
            AddSchemaVersionCommand duplicateCommand = AddSchemaVersionCommand.of(
                    existingSchema.id(),
                    VersionIdentifier.of("1.0.0"),
                    definition2);

            assertThatThrownBy(() -> handler.handle(duplicateCommand))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when definition is empty")
        void throwsWhenDefinitionIsEmpty() {
            Content emptyDefinition = Content.of(new byte[0], Format.JSON);
            AddSchemaVersionCommand command = AddSchemaVersionCommand.of(
                    existingSchema.id(),
                    VersionIdentifier.of("1.0.0"),
                    emptyDefinition);

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }
    }
}
