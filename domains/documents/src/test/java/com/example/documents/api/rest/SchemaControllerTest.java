package com.example.documents.api.rest;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.documents.api.dto.AddSchemaVersionRequest;
import com.example.documents.api.dto.CreateSchemaRequest;
import com.example.documents.api.dto.SchemaResponse;
import com.example.documents.api.dto.SchemaVersionResponse;
import com.example.documents.application.command.AddSchemaVersionCommand;
import com.example.documents.application.command.CreateSchemaCommand;
import com.example.documents.application.handler.SchemaCommandHandler;
import com.example.documents.application.handler.SchemaNotFoundException;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersion;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.SchemaRepository;

/**
 * Unit tests for {@link SchemaController}.
 *
 * <p>Tests the createSchema and getSchema endpoint behaviours
 * including request mapping, command construction, and response mapping.</p>
 *
 * <p><b>Property 1: Valid Create Requests Return 201 Created</b></p>
 * <p><b>Validates: Requirements 6.1</b></p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaController")
class SchemaControllerTest {

    @Mock
    private SchemaCommandHandler commandHandler;

    @Mock
    private SchemaRepository repository;

    private SchemaController controller;

    @BeforeEach
    void setUp() {
        controller = new SchemaController(commandHandler, repository);
    }

    @Nested
    @DisplayName("POST /api/schemas (createSchema)")
    class CreateSchemaTests {

        @Test
        @DisplayName("should return 201 Created with valid request")
        void validRequestReturns201Created() {
            // Given
            CreateSchemaRequest request = createValidRequest();
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should return response with correct schema ID")
        void responseContainsCorrectSchemaId() {
            // Given
            CreateSchemaRequest request = createValidRequest();
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(schema.id().value());
        }

        @Test
        @DisplayName("should return response with correct schema name")
        void responseContainsCorrectSchemaName() {
            // Given
            CreateSchemaRequest request = createValidRequest();
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().name()).isEqualTo(request.name());
        }

        @Test
        @DisplayName("should return response with correct schema format")
        void responseContainsCorrectSchemaFormat() {
            // Given
            CreateSchemaRequest request = createValidRequest();
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().format()).isEqualTo(request.format());
        }

        @Test
        @DisplayName("should return response with empty versions list for new schema")
        void responseContainsEmptyVersionsList() {
            // Given
            CreateSchemaRequest request = createValidRequest();
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versions()).isEmpty();
        }

        @Test
        @DisplayName("should pass correct command to handler")
        void passesCorrectCommandToHandler() {
            // Given
            CreateSchemaRequest request = createValidRequest();
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            controller.createSchema(request);

            // Then
            ArgumentCaptor<CreateSchemaCommand> commandCaptor = 
                ArgumentCaptor.forClass(CreateSchemaCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            CreateSchemaCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.name()).isEqualTo(request.name());
            assertThat(capturedCommand.format()).isEqualTo(request.format());
        }

        @Test
        @DisplayName("should handle XSD format correctly")
        void handlesXsdFormatCorrectly() {
            // Given
            CreateSchemaRequest request = new CreateSchemaRequest("Invoice Schema", SchemaFormat.XSD);
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().format()).isEqualTo(SchemaFormat.XSD);
        }

        @Test
        @DisplayName("should handle JSON_SCHEMA format correctly")
        void handlesJsonSchemaFormatCorrectly() {
            // Given
            CreateSchemaRequest request = new CreateSchemaRequest("Order Schema", SchemaFormat.JSON_SCHEMA);
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().format()).isEqualTo(SchemaFormat.JSON_SCHEMA);
        }

        @Test
        @DisplayName("should handle RELAXNG format correctly")
        void handlesRelaxNgFormatCorrectly() {
            // Given
            CreateSchemaRequest request = new CreateSchemaRequest("Document Schema", SchemaFormat.RELAXNG);
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().format()).isEqualTo(SchemaFormat.RELAXNG);
        }

        @Test
        @DisplayName("should handle schema names with special characters")
        void handlesSchemaNamesWithSpecialCharacters() {
            // Given
            CreateSchemaRequest request = new CreateSchemaRequest(
                "UBL-Invoice-2.1", 
                SchemaFormat.XSD
            );
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().name()).isEqualTo("UBL-Invoice-2.1");
        }

        @Test
        @DisplayName("should handle schema names with spaces")
        void handlesSchemaNamesWithSpaces() {
            // Given
            CreateSchemaRequest request = new CreateSchemaRequest(
                "Purchase Order Schema", 
                SchemaFormat.JSON_SCHEMA
            );
            Schema schema = createTestSchema(request);
            when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

            // When
            ResponseEntity<SchemaResponse> response = controller.createSchema(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().name()).isEqualTo("Purchase Order Schema");
        }
    }

    @Nested
    @DisplayName("GET /api/schemas/{id} (getSchema)")
    class GetSchemaTests {

        @Test
        @DisplayName("should return 200 OK when schema is found")
        void foundReturns200Ok() {
            // Given
            Schema schema = createTestSchemaWithDefaults();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaResponse> response = controller.getSchema(schema.id().value());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return response with correct schema ID")
        void responseContainsCorrectSchemaId() {
            // Given
            Schema schema = createTestSchemaWithDefaults();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaResponse> response = controller.getSchema(schema.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(schema.id().value());
        }

        @Test
        @DisplayName("should return response with correct schema name")
        void responseContainsCorrectSchemaName() {
            // Given
            Schema schema = createTestSchemaWithDefaults();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaResponse> response = controller.getSchema(schema.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().name()).isEqualTo(schema.name());
        }

        @Test
        @DisplayName("should return response with correct schema format")
        void responseContainsCorrectSchemaFormat() {
            // Given
            Schema schema = createTestSchemaWithDefaults();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaResponse> response = controller.getSchema(schema.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().format()).isEqualTo(schema.format());
        }

        @Test
        @DisplayName("should return response with empty versions list for schema without versions")
        void responseContainsEmptyVersionsListForSchemaWithoutVersions() {
            // Given
            Schema schema = createTestSchemaWithDefaults();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaResponse> response = controller.getSchema(schema.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versions()).isEmpty();
        }

        @Test
        @DisplayName("should throw SchemaNotFoundException when schema not found")
        void notFoundThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> controller.getSchema(nonExistentId))
                .isInstanceOf(SchemaNotFoundException.class);
        }

        @Test
        @DisplayName("should query repository with correct SchemaId")
        void queriesRepositoryWithCorrectId() {
            // Given
            Schema schema = createTestSchemaWithDefaults();
            UUID requestedId = schema.id().value();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            controller.getSchema(requestedId);

            // Then
            ArgumentCaptor<SchemaId> idCaptor = ArgumentCaptor.forClass(SchemaId.class);
            verify(repository).findById(idCaptor.capture());
            assertThat(idCaptor.getValue().value()).isEqualTo(requestedId);
        }
    }

    @Nested
    @DisplayName("GET /api/schemas/{schemaId}/versions/{versionId} (getVersion)")
    class GetVersionTests {

        @Test
        @DisplayName("should return 200 OK when version is found")
        void foundReturns200Ok() {
            // Given
            Schema schema = createTestSchemaWithVersion();
            SchemaVersion version = schema.versions().get(0);
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaVersionResponse> response = 
                controller.getVersion(schema.id().value(), version.versionIdentifier().value());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return response with correct version ID")
        void responseContainsCorrectVersionId() {
            // Given
            Schema schema = createTestSchemaWithVersion();
            SchemaVersion version = schema.versions().get(0);
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaVersionResponse> response = 
                controller.getVersion(schema.id().value(), version.versionIdentifier().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(version.id().value());
        }

        @Test
        @DisplayName("should return response with correct version identifier")
        void responseContainsCorrectVersionIdentifier() {
            // Given
            Schema schema = createTestSchemaWithVersion();
            SchemaVersion version = schema.versions().get(0);
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaVersionResponse> response = 
                controller.getVersion(schema.id().value(), version.versionIdentifier().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionIdentifier()).isEqualTo(version.versionIdentifier().value());
        }

        @Test
        @DisplayName("should return response with creation timestamp")
        void responseContainsCreationTimestamp() {
            // Given
            Schema schema = createTestSchemaWithVersion();
            SchemaVersion version = schema.versions().get(0);
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaVersionResponse> response = 
                controller.getVersion(schema.id().value(), version.versionIdentifier().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdAt()).isNotNull();
            assertThat(response.getBody().createdAt()).isEqualTo(version.createdAt());
        }

        @Test
        @DisplayName("should return response with deprecated flag")
        void responseContainsDeprecatedFlag() {
            // Given
            Schema schema = createTestSchemaWithVersion();
            SchemaVersion version = schema.versions().get(0);
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaVersionResponse> response = 
                controller.getVersion(schema.id().value(), version.versionIdentifier().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().deprecated()).isEqualTo(version.isDeprecated());
        }

        @Test
        @DisplayName("should throw SchemaNotFoundException when schema not found")
        void schemaNotFoundThrowsException() {
            // Given
            UUID nonExistentSchemaId = UUID.randomUUID();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> controller.getVersion(nonExistentSchemaId, "1.0.0"))
                .isInstanceOf(SchemaNotFoundException.class);
        }

        @Test
        @DisplayName("should throw SchemaVersionNotFoundException when version not found")
        void versionNotFoundThrowsException() {
            // Given
            Schema schema = createTestSchemaWithVersion();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When/Then
            assertThatThrownBy(() -> controller.getVersion(schema.id().value(), "99.99.99"))
                .isInstanceOf(com.example.documents.application.handler.SchemaVersionNotFoundException.class);
        }

        @Test
        @DisplayName("should query repository with correct SchemaId")
        void queriesRepositoryWithCorrectId() {
            // Given
            Schema schema = createTestSchemaWithVersion();
            SchemaVersion version = schema.versions().get(0);
            UUID requestedId = schema.id().value();
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            controller.getVersion(requestedId, version.versionIdentifier().value());

            // Then
            ArgumentCaptor<SchemaId> idCaptor = ArgumentCaptor.forClass(SchemaId.class);
            verify(repository).findById(idCaptor.capture());
            assertThat(idCaptor.getValue().value()).isEqualTo(requestedId);
        }

        @Test
        @DisplayName("should handle semantic version identifiers")
        void handlesSemanticVersionIdentifiers() {
            // Given
            Schema schema = createTestSchemaWithVersionIdentifier("2.1.3");
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaVersionResponse> response = 
                controller.getVersion(schema.id().value(), "2.1.3");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionIdentifier()).isEqualTo("2.1.3");
        }

        @Test
        @DisplayName("should handle custom version identifiers")
        void handlesCustomVersionIdentifiers() {
            // Given
            Schema schema = createTestSchemaWithVersionIdentifier("v2024.01");
            when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

            // When
            ResponseEntity<SchemaVersionResponse> response = 
                controller.getVersion(schema.id().value(), "v2024.01");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionIdentifier()).isEqualTo("v2024.01");
        }
    }

    @Nested
    @DisplayName("POST /api/schemas/{schemaId}/versions (addVersion)")
    class AddVersionTests {

        @Test
        @DisplayName("should return 201 Created with valid request")
        void validRequestReturns201Created() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = createValidAddSchemaVersionRequest();
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            ResponseEntity<SchemaVersionResponse> response = controller.addVersion(schemaId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should return response with correct version ID")
        void responseContainsCorrectVersionId() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = createValidAddSchemaVersionRequest();
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            ResponseEntity<SchemaVersionResponse> response = controller.addVersion(schemaId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(schemaVersion.id().value());
        }

        @Test
        @DisplayName("should return response with correct version identifier")
        void responseContainsCorrectVersionIdentifier() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = createValidAddSchemaVersionRequest();
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            ResponseEntity<SchemaVersionResponse> response = controller.addVersion(schemaId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionIdentifier()).isEqualTo(request.versionIdentifier());
        }

        @Test
        @DisplayName("should return response with non-null creation timestamp")
        void responseContainsCreationTimestamp() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = createValidAddSchemaVersionRequest();
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            ResponseEntity<SchemaVersionResponse> response = controller.addVersion(schemaId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should return response with deprecated flag set to false for new version")
        void responseContainsDeprecatedFlagFalse() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = createValidAddSchemaVersionRequest();
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            ResponseEntity<SchemaVersionResponse> response = controller.addVersion(schemaId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().deprecated()).isFalse();
        }

        @Test
        @DisplayName("should pass correct command to handler")
        void passesCorrectCommandToHandler() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = createValidAddSchemaVersionRequest();
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            controller.addVersion(schemaId, request);

            // Then
            ArgumentCaptor<AddSchemaVersionCommand> commandCaptor = 
                ArgumentCaptor.forClass(AddSchemaVersionCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            AddSchemaVersionCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.schemaId().value()).isEqualTo(schemaId);
            assertThat(capturedCommand.version().value()).isEqualTo(request.versionIdentifier());
        }

        @Test
        @DisplayName("should decode Base64 content correctly")
        void decodesBase64ContentCorrectly() {
            // Given
            UUID schemaId = UUID.randomUUID();
            String originalDefinition = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"></xs:schema>";
            String base64Definition = java.util.Base64.getEncoder().encodeToString(originalDefinition.getBytes());
            AddSchemaVersionRequest request = new AddSchemaVersionRequest("1.0.0", base64Definition);
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            controller.addVersion(schemaId, request);

            // Then
            ArgumentCaptor<AddSchemaVersionCommand> commandCaptor = 
                ArgumentCaptor.forClass(AddSchemaVersionCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            AddSchemaVersionCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.definition().data()).isEqualTo(originalDefinition.getBytes());
        }

        @Test
        @DisplayName("should throw SchemaNotFoundException when schema not found")
        void schemaNotFoundThrowsException() {
            // Given
            UUID nonExistentSchemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = createValidAddSchemaVersionRequest();
            when(commandHandler.handle(any(AddSchemaVersionCommand.class)))
                .thenThrow(new SchemaNotFoundException(new SchemaId(nonExistentSchemaId)));

            // When/Then
            assertThatThrownBy(() -> controller.addVersion(nonExistentSchemaId, request))
                .isInstanceOf(SchemaNotFoundException.class);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when duplicate version identifier")
        void duplicateVersionThrowsException() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = createValidAddSchemaVersionRequest();
            when(commandHandler.handle(any(AddSchemaVersionCommand.class)))
                .thenThrow(new IllegalArgumentException("Schema version 1.0.0 already exists for schema Test Schema"));

            // When/Then
            assertThatThrownBy(() -> controller.addVersion(schemaId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should handle semantic version identifiers")
        void handlesSemanticVersionIdentifiers() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "2.1.3", 
                java.util.Base64.getEncoder().encodeToString("schema definition".getBytes())
            );
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            ResponseEntity<SchemaVersionResponse> response = controller.addVersion(schemaId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionIdentifier()).isEqualTo("2.1.3");
        }

        @Test
        @DisplayName("should handle custom version identifiers")
        void handlesCustomVersionIdentifiers() {
            // Given
            UUID schemaId = UUID.randomUUID();
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "v2024.01", 
                java.util.Base64.getEncoder().encodeToString("schema definition".getBytes())
            );
            SchemaVersion schemaVersion = createTestSchemaVersion(request);
            when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

            // When
            ResponseEntity<SchemaVersionResponse> response = controller.addVersion(schemaId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionIdentifier()).isEqualTo("v2024.01");
        }
    }

    // Helper methods

    private CreateSchemaRequest createValidRequest() {
        return new CreateSchemaRequest("Invoice Schema", SchemaFormat.XSD);
    }

    private AddSchemaVersionRequest createValidAddSchemaVersionRequest() {
        String definition = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"></xs:schema>";
        String base64Definition = java.util.Base64.getEncoder().encodeToString(definition.getBytes());
        return new AddSchemaVersionRequest("1.0.0", base64Definition);
    }

    private Schema createTestSchema(CreateSchemaRequest request) {
        return Schema.create(request.name(), request.format());
    }

    private Schema createTestSchemaWithDefaults() {
        return Schema.create("Test Schema", SchemaFormat.XSD);
    }

    private Schema createTestSchemaWithVersion() {
        Schema schema = Schema.create("Test Schema", SchemaFormat.XSD);
        byte[] definitionBytes = "schema definition".getBytes();
        Content definition = Content.of(definitionBytes, Format.XML);
        ContentRef definitionRef = ContentRef.of(definition.hash());
        schema.addVersion(VersionIdentifier.of("1.0.0"), definitionRef);
        return schema;
    }

    private Schema createTestSchemaWithVersionIdentifier(String versionIdentifier) {
        Schema schema = Schema.create("Test Schema", SchemaFormat.XSD);
        byte[] definitionBytes = "schema definition".getBytes();
        Content definition = Content.of(definitionBytes, Format.XML);
        ContentRef definitionRef = ContentRef.of(definition.hash());
        schema.addVersion(VersionIdentifier.of(versionIdentifier), definitionRef);
        return schema;
    }

    private SchemaVersion createTestSchemaVersion(AddSchemaVersionRequest request) {
        byte[] definitionBytes = java.util.Base64.getDecoder().decode(request.definition());
        Content definition = Content.of(definitionBytes, Format.XML);
        ContentRef definitionRef = ContentRef.of(definition.hash());
        return SchemaVersion.create(VersionIdentifier.of(request.versionIdentifier()), definitionRef);
    }
}
