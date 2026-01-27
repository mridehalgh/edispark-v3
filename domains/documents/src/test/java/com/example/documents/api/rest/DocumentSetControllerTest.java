package com.example.documents.api.rest;

import java.util.Base64;
import java.util.List;
import java.util.Map;
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

import com.example.documents.api.dto.AddDocumentRequest;
import com.example.documents.api.dto.AddVersionRequest;
import com.example.documents.api.dto.CreateDerivativeRequest;
import com.example.documents.api.dto.CreateDocumentSetRequest;
import com.example.documents.api.dto.DerivativeResponse;
import com.example.documents.api.dto.DocumentResponse;
import com.example.documents.api.dto.DocumentSetResponse;
import com.example.documents.api.dto.DocumentVersionResponse;
import com.example.documents.application.command.AddDocumentCommand;
import com.example.documents.application.command.AddVersionCommand;
import com.example.documents.application.command.CreateDerivativeCommand;
import com.example.documents.application.command.CreateDocumentSetCommand;
import com.example.documents.application.handler.DocumentSetCommandHandler;
import com.example.documents.application.handler.DocumentNotFoundException;
import com.example.documents.application.handler.DocumentSetNotFoundException;
import com.example.documents.application.handler.VersionNotFoundException;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.Derivative;
import com.example.documents.domain.model.DerivativeId;
import com.example.documents.domain.model.Document;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.model.DocumentVersionId;
import com.example.documents.domain.model.DuplicateDerivativeException;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.TransformationMethod;
import com.example.documents.domain.model.ValidationResult;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.DocumentSetRepository;

/**
 * Unit tests for {@link DocumentSetController}.
 *
 * <p>Tests the createDocumentSet, getDocumentSet, and addDocument endpoint behaviours
 * including request mapping, command construction, and response mapping.</p>
 *
 * <p>Validates: Requirements 1.1, 1.2, 2.1</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentSetController")
class DocumentSetControllerTest {

    @Mock
    private DocumentSetCommandHandler commandHandler;

    @Mock
    private DocumentSetRepository repository;

    private DocumentSetController controller;

    @BeforeEach
    void setUp() {
        controller = new DocumentSetController(commandHandler, repository);
    }

    @Nested
    @DisplayName("POST /api/document-sets (createDocumentSet)")
    class CreateDocumentSetTests {

        @Test
        @DisplayName("should return 201 Created with valid request")
        void validRequestReturns201Created() {
            // Given
            CreateDocumentSetRequest request = createValidRequest();
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should return response with correct document set ID")
        void responseContainsCorrectDocumentSetId() {
            // Given
            CreateDocumentSetRequest request = createValidRequest();
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(documentSet.id().value());
        }

        @Test
        @DisplayName("should return response with correct createdAt timestamp")
        void responseContainsCorrectCreatedAtTimestamp() {
            // Given
            CreateDocumentSetRequest request = createValidRequest();
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdAt()).isEqualTo(documentSet.createdAt());
        }

        @Test
        @DisplayName("should return response with correct createdBy")
        void responseContainsCorrectCreatedBy() {
            // Given
            CreateDocumentSetRequest request = createValidRequest();
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdBy()).isEqualTo(request.createdBy());
        }

        @Test
        @DisplayName("should return response with correct metadata")
        void responseContainsCorrectMetadata() {
            // Given
            Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");
            CreateDocumentSetRequest request = createValidRequestWithMetadata(metadata);
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().metadata()).isEqualTo(metadata);
        }

        @Test
        @DisplayName("should return response with document summaries")
        void responseContainsDocumentSummaries() {
            // Given
            CreateDocumentSetRequest request = createValidRequest();
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().documents()).hasSize(1);
            
            DocumentSetResponse.DocumentSummary summary = response.getBody().documents().get(0);
            assertThat(summary.type()).isEqualTo(request.documentType());
            assertThat(summary.versionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should pass correct command to handler")
        void passesCorrectCommandToHandler() {
            // Given
            CreateDocumentSetRequest request = createValidRequest();
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            controller.createDocumentSet(request);

            // Then
            ArgumentCaptor<CreateDocumentSetCommand> commandCaptor = 
                ArgumentCaptor.forClass(CreateDocumentSetCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            CreateDocumentSetCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.initialDocumentType()).isEqualTo(request.documentType());
            assertThat(capturedCommand.createdBy()).isEqualTo(request.createdBy());
            assertThat(capturedCommand.metadata()).isEqualTo(request.metadata());
        }

        @Test
        @DisplayName("should decode Base64 content correctly")
        void decodesBase64ContentCorrectly() {
            // Given
            String originalContent = "test document content";
            String base64Content = Base64.getEncoder().encodeToString(originalContent.getBytes());
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                base64Content,
                "user@example.com",
                null
            );
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            controller.createDocumentSet(request);

            // Then
            ArgumentCaptor<CreateDocumentSetCommand> commandCaptor = 
                ArgumentCaptor.forClass(CreateDocumentSetCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            CreateDocumentSetCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.initialContent().data())
                .isEqualTo(originalContent.getBytes());
        }

        @Test
        @DisplayName("should create correct schema version reference")
        void createsCorrectSchemaVersionReference() {
            // Given
            UUID schemaId = UUID.randomUUID();
            String schemaVersion = "2.1.0";
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.ORDER,
                schemaId,
                schemaVersion,
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            controller.createDocumentSet(request);

            // Then
            ArgumentCaptor<CreateDocumentSetCommand> commandCaptor = 
                ArgumentCaptor.forClass(CreateDocumentSetCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            CreateDocumentSetCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.schemaRef().schemaId().value()).isEqualTo(schemaId);
            assertThat(capturedCommand.schemaRef().version().value()).isEqualTo(schemaVersion);
        }

        @Test
        @DisplayName("should handle null metadata in request")
        void handlesNullMetadataInRequest() {
            // Given
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should handle empty metadata in request")
        void handlesEmptyMetadataInRequest() {
            // Given
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                Map.of()
            );
            DocumentSet documentSet = createTestDocumentSet(request);
            when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

            // When
            ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().metadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /api/document-sets/{id} (getDocumentSet)")
    class GetDocumentSetTests {

        @Test
        @DisplayName("should return 200 OK when document set is found")
        void foundReturns200Ok() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentSetResponse> response = controller.getDocumentSet(documentSet.id().value());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return response with correct document set ID")
        void responseContainsCorrectDocumentSetId() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentSetResponse> response = controller.getDocumentSet(documentSet.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(documentSet.id().value());
        }

        @Test
        @DisplayName("should return response with correct createdAt timestamp")
        void responseContainsCorrectCreatedAtTimestamp() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentSetResponse> response = controller.getDocumentSet(documentSet.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdAt()).isEqualTo(documentSet.createdAt());
        }

        @Test
        @DisplayName("should return response with correct createdBy")
        void responseContainsCorrectCreatedBy() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentSetResponse> response = controller.getDocumentSet(documentSet.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdBy()).isEqualTo(documentSet.createdBy());
        }

        @Test
        @DisplayName("should return response with correct metadata")
        void responseContainsCorrectMetadata() {
            // Given
            Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");
            DocumentSet documentSet = createTestDocumentSetWithMetadata(metadata);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentSetResponse> response = controller.getDocumentSet(documentSet.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().metadata()).isEqualTo(metadata);
        }

        @Test
        @DisplayName("should return response with document summaries")
        void responseContainsDocumentSummaries() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentSetResponse> response = controller.getDocumentSet(documentSet.id().value());

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().documents()).hasSize(1);
            
            DocumentSetResponse.DocumentSummary summary = response.getBody().documents().get(0);
            assertThat(summary.type()).isEqualTo(DocumentType.INVOICE);
            assertThat(summary.versionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw DocumentSetNotFoundException when document set not found")
        void notFoundThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> controller.getDocumentSet(nonExistentId))
                .isInstanceOf(DocumentSetNotFoundException.class);
        }

        @Test
        @DisplayName("should query repository with correct DocumentSetId")
        void queriesRepositoryWithCorrectId() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            UUID requestedId = documentSet.id().value();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            controller.getDocumentSet(requestedId);

            // Then
            ArgumentCaptor<DocumentSetId> idCaptor = ArgumentCaptor.forClass(DocumentSetId.class);
            verify(repository).findById(idCaptor.capture());
            assertThat(idCaptor.getValue().value()).isEqualTo(requestedId);
        }
    }

    @Nested
    @DisplayName("POST /api/document-sets/{setId}/documents (addDocument)")
    class AddDocumentTests {

        @Test
        @DisplayName("should return 201 Created with valid request")
        void validRequestReturns201Created() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should return response with correct document ID")
        void responseContainsCorrectDocumentId() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(document.id().value());
        }

        @Test
        @DisplayName("should return response with correct document type")
        void responseContainsCorrectDocumentType() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().type()).isEqualTo(request.documentType());
        }

        @Test
        @DisplayName("should return response with correct schema reference")
        void responseContainsCorrectSchemaReference() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().schemaRef()).isNotNull();
            assertThat(response.getBody().schemaRef().schemaId()).isEqualTo(request.schemaId());
            assertThat(response.getBody().schemaRef().version()).isEqualTo(request.schemaVersion());
        }

        @Test
        @DisplayName("should return response with version count of 1 for new document")
        void responseContainsVersionCountOfOne() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return response with current version details")
        void responseContainsCurrentVersionDetails() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().currentVersion()).isNotNull();
            assertThat(response.getBody().currentVersion().versionNumber()).isEqualTo(1);
            assertThat(response.getBody().currentVersion().createdBy()).isEqualTo(request.createdBy());
        }

        @Test
        @DisplayName("should return response with empty derivatives list for new document")
        void responseContainsEmptyDerivativesList() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().derivatives()).isEmpty();
        }

        @Test
        @DisplayName("should pass correct command to handler")
        void passesCorrectCommandToHandler() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            controller.addDocument(setId, request);

            // Then
            ArgumentCaptor<AddDocumentCommand> commandCaptor = 
                ArgumentCaptor.forClass(AddDocumentCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            AddDocumentCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.documentSetId().value()).isEqualTo(setId);
            assertThat(capturedCommand.type()).isEqualTo(request.documentType());
            assertThat(capturedCommand.createdBy()).isEqualTo(request.createdBy());
        }

        @Test
        @DisplayName("should decode Base64 content correctly")
        void decodesBase64ContentCorrectly() {
            // Given
            UUID setId = UUID.randomUUID();
            String originalContent = "test document content for add";
            String base64Content = Base64.getEncoder().encodeToString(originalContent.getBytes());
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.ORDER,
                UUID.randomUUID(),
                "1.0.0",
                base64Content,
                "user@example.com",
                null
            );
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            controller.addDocument(setId, request);

            // Then
            ArgumentCaptor<AddDocumentCommand> commandCaptor = 
                ArgumentCaptor.forClass(AddDocumentCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            AddDocumentCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.content().data())
                .isEqualTo(originalContent.getBytes());
        }

        @Test
        @DisplayName("should create correct schema version reference")
        void createsCorrectSchemaVersionReference() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID schemaId = UUID.randomUUID();
            String schemaVersion = "2.1.0";
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.ORDER,
                schemaId,
                schemaVersion,
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            controller.addDocument(setId, request);

            // Then
            ArgumentCaptor<AddDocumentCommand> commandCaptor = 
                ArgumentCaptor.forClass(AddDocumentCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            AddDocumentCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.schemaRef().schemaId().value()).isEqualTo(schemaId);
            assertThat(capturedCommand.schemaRef().version().value()).isEqualTo(schemaVersion);
        }

        @Test
        @DisplayName("should handle null relatedDocumentId in request")
        void handlesNullRelatedDocumentId() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("should pass relatedDocumentId to command when provided")
        void passesRelatedDocumentIdToCommand() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID relatedDocId = UUID.randomUUID();
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                relatedDocId
            );
            Document document = createTestDocument(request);
            when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

            // When
            controller.addDocument(setId, request);

            // Then
            ArgumentCaptor<AddDocumentCommand> commandCaptor = 
                ArgumentCaptor.forClass(AddDocumentCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            AddDocumentCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.relatedDocumentId()).isNotNull();
            assertThat(capturedCommand.relatedDocumentId().value()).isEqualTo(relatedDocId);
        }

        @Test
        @DisplayName("should throw DocumentSetNotFoundException when document set not found")
        void documentSetNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            AddDocumentRequest request = createValidAddDocumentRequest();
            when(commandHandler.handle(any(AddDocumentCommand.class)))
                .thenThrow(new DocumentSetNotFoundException(new DocumentSetId(setId)));

            // When/Then
            assertThatThrownBy(() -> controller.addDocument(setId, request))
                .isInstanceOf(DocumentSetNotFoundException.class);
        }
    }

    /**
     * Unit tests for POST /api/document-sets/{setId}/documents/{docId}/versions (addVersion).
     *
     * <p>Tests the addVersion endpoint behaviour including request mapping,
     * command construction, and response mapping.</p>
     *
     * <p><b>Validates: Requirements 3.1, 3.2</b></p>
     */
    @Nested
    @DisplayName("POST /api/document-sets/{setId}/documents/{docId}/versions (addVersion)")
    class AddVersionTests {

        @Test
        @DisplayName("should return 201 Created with valid request")
        void validRequestReturns201Created() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            DocumentVersion version = createTestDocumentVersion(request, 2);
            when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should return response with correct version ID")
        void responseContainsCorrectVersionId() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            DocumentVersion version = createTestDocumentVersion(request, 2);
            when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(version.id().value());
        }

        @Test
        @DisplayName("should return response with correct version number")
        void responseContainsCorrectVersionNumber() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            DocumentVersion version = createTestDocumentVersion(request, 2);
            when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionNumber()).isEqualTo(version.versionNumber());
        }

        @Test
        @DisplayName("should return response with correct content hash")
        void responseContainsCorrectContentHash() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            DocumentVersion version = createTestDocumentVersion(request, 2);
            when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().contentHash()).isEqualTo(version.contentHash().toFullString());
        }

        @Test
        @DisplayName("should return response with correct createdBy")
        void responseContainsCorrectCreatedBy() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            DocumentVersion version = createTestDocumentVersion(request, 2);
            when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdBy()).isEqualTo(request.createdBy());
        }

        @Test
        @DisplayName("should return response with correct createdAt timestamp")
        void responseContainsCorrectCreatedAtTimestamp() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            DocumentVersion version = createTestDocumentVersion(request, 2);
            when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdAt()).isEqualTo(version.createdAt());
        }

        @Test
        @DisplayName("should pass correct command to handler")
        void passesCorrectCommandToHandler() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            DocumentVersion version = createTestDocumentVersion(request, 2);
            when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

            // When
            controller.addVersion(setId, docId, request);

            // Then
            ArgumentCaptor<AddVersionCommand> commandCaptor = 
                ArgumentCaptor.forClass(AddVersionCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            AddVersionCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.documentSetId().value()).isEqualTo(setId);
            assertThat(capturedCommand.documentId().value()).isEqualTo(docId);
            assertThat(capturedCommand.createdBy()).isEqualTo(request.createdBy());
        }

        @Test
        @DisplayName("should decode Base64 content correctly")
        void decodesBase64ContentCorrectly() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            String originalContent = "updated document content for version";
            String base64Content = Base64.getEncoder().encodeToString(originalContent.getBytes());
            AddVersionRequest request = new AddVersionRequest(base64Content, "user@example.com");
            DocumentVersion version = createTestDocumentVersion(request, 2);
            when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

            // When
            controller.addVersion(setId, docId, request);

            // Then
            ArgumentCaptor<AddVersionCommand> commandCaptor = 
                ArgumentCaptor.forClass(AddVersionCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            AddVersionCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.content().data())
                .isEqualTo(originalContent.getBytes());
        }

        @Test
        @DisplayName("should throw DocumentNotFoundException when document not found")
        void documentNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            when(commandHandler.handle(any(AddVersionCommand.class)))
                .thenThrow(new DocumentNotFoundException(new DocumentSetId(setId), new DocumentId(docId)));

            // When/Then
            assertThatThrownBy(() -> controller.addVersion(setId, docId, request))
                .isInstanceOf(DocumentNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DocumentSetNotFoundException when document set not found")
        void documentSetNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            AddVersionRequest request = createValidAddVersionRequest();
            when(commandHandler.handle(any(AddVersionCommand.class)))
                .thenThrow(new DocumentSetNotFoundException(new DocumentSetId(setId)));

            // When/Then
            assertThatThrownBy(() -> controller.addVersion(setId, docId, request))
                .isInstanceOf(DocumentSetNotFoundException.class);
        }
    }

    /**
     * Unit tests for GET /api/document-sets/{setId}/documents/{docId}/versions/{versionNumber} (getVersion).
     *
     * <p>Tests the getVersion endpoint behaviour including request mapping,
     * response mapping, and exception handling.</p>
     *
     * <p><b>Property 2: Create-Retrieve Round Trip</b></p>
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Nested
    @DisplayName("GET /api/document-sets/{setId}/documents/{docId}/versions/{versionNumber} (getVersion)")
    class GetVersionTests {

        @Test
        @DisplayName("should return 200 OK when version is found")
        void foundReturns200Ok() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            DocumentVersion version = document.getCurrentVersion();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.getVersion(
                    documentSet.id().value(),
                    document.id().value(),
                    version.versionNumber()
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return response with correct version ID")
        void responseContainsCorrectVersionId() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            DocumentVersion version = document.getCurrentVersion();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.getVersion(
                    documentSet.id().value(),
                    document.id().value(),
                    version.versionNumber()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(version.id().value());
        }

        @Test
        @DisplayName("should return response with correct version number")
        void responseContainsCorrectVersionNumber() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            DocumentVersion version = document.getCurrentVersion();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.getVersion(
                    documentSet.id().value(),
                    document.id().value(),
                    version.versionNumber()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionNumber()).isEqualTo(version.versionNumber());
        }

        @Test
        @DisplayName("should return response with correct content hash")
        void responseContainsCorrectContentHash() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            DocumentVersion version = document.getCurrentVersion();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.getVersion(
                    documentSet.id().value(),
                    document.id().value(),
                    version.versionNumber()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().contentHash()).isEqualTo(version.contentHash().toFullString());
        }

        @Test
        @DisplayName("should return response with correct createdBy")
        void responseContainsCorrectCreatedBy() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            DocumentVersion version = document.getCurrentVersion();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.getVersion(
                    documentSet.id().value(),
                    document.id().value(),
                    version.versionNumber()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdBy()).isEqualTo(version.createdBy());
        }

        @Test
        @DisplayName("should return response with correct createdAt timestamp")
        void responseContainsCorrectCreatedAtTimestamp() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            DocumentVersion version = document.getCurrentVersion();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentVersionResponse> response = controller.getVersion(
                    documentSet.id().value(),
                    document.id().value(),
                    version.versionNumber()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdAt()).isEqualTo(version.createdAt());
        }

        @Test
        @DisplayName("should throw DocumentSetNotFoundException when document set not found")
        void documentSetNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> controller.getVersion(setId, docId, versionNumber))
                    .isInstanceOf(DocumentSetNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DocumentNotFoundException when document not found within set")
        void documentNotFoundThrowsException() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            UUID nonExistentDocId = UUID.randomUUID();
            int versionNumber = 1;
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When/Then
            assertThatThrownBy(() -> controller.getVersion(documentSet.id().value(), nonExistentDocId, versionNumber))
                    .isInstanceOf(DocumentNotFoundException.class);
        }

        @Test
        @DisplayName("should throw VersionNotFoundException when version not found within document")
        void versionNotFoundThrowsException() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            int nonExistentVersionNumber = 999;
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When/Then
            assertThatThrownBy(() -> controller.getVersion(
                    documentSet.id().value(),
                    document.id().value(),
                    nonExistentVersionNumber
            )).isInstanceOf(VersionNotFoundException.class);
        }

        @Test
        @DisplayName("should query repository with correct DocumentSetId")
        void queriesRepositoryWithCorrectDocumentSetId() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            DocumentVersion version = document.getCurrentVersion();
            UUID requestedSetId = documentSet.id().value();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            controller.getVersion(requestedSetId, document.id().value(), version.versionNumber());

            // Then
            ArgumentCaptor<DocumentSetId> idCaptor = ArgumentCaptor.forClass(DocumentSetId.class);
            verify(repository).findById(idCaptor.capture());
            assertThat(idCaptor.getValue().value()).isEqualTo(requestedSetId);
        }
    }

    /**
     * Unit tests for POST /api/document-sets/{setId}/documents/{docId}/derivatives (createDerivative).
     *
     * <p>Tests the createDerivative endpoint behaviour including request mapping,
     * command construction, response mapping, and duplicate derivative handling.</p>
     *
     * <p><b>Property 5: Duplicate Creation Returns 409 Conflict</b></p>
     * <p><b>Validates: Requirements 4.1, 4.3</b></p>
     */
    @Nested
    @DisplayName("POST /api/document-sets/{setId}/documents/{docId}/derivatives (createDerivative)")
    class CreateDerivativeTests {

        @Test
        @DisplayName("should return 201 Created with valid request")
        void validRequestReturns201Created() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            Derivative derivative = createTestDerivative(request);
            when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

            // When
            ResponseEntity<DerivativeResponse> response = controller.createDerivative(setId, docId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("should return response with correct derivative ID")
        void responseContainsCorrectDerivativeId() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            Derivative derivative = createTestDerivative(request);
            when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

            // When
            ResponseEntity<DerivativeResponse> response = controller.createDerivative(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(derivative.id().value());
        }

        @Test
        @DisplayName("should return response with correct target format")
        void responseContainsCorrectTargetFormat() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            Derivative derivative = createTestDerivative(request);
            when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

            // When
            ResponseEntity<DerivativeResponse> response = controller.createDerivative(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().targetFormat()).isEqualTo(request.targetFormat());
        }

        @Test
        @DisplayName("should return response with correct source version ID")
        void responseContainsCorrectSourceVersionId() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            Derivative derivative = createTestDerivative(request);
            when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

            // When
            ResponseEntity<DerivativeResponse> response = controller.createDerivative(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().sourceVersionId()).isEqualTo(derivative.sourceVersionId().value());
        }

        @Test
        @DisplayName("should return response with correct content hash")
        void responseContainsCorrectContentHash() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            Derivative derivative = createTestDerivative(request);
            when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

            // When
            ResponseEntity<DerivativeResponse> response = controller.createDerivative(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().contentHash()).isEqualTo(derivative.contentHash().toFullString());
        }

        @Test
        @DisplayName("should return response with correct transformation method")
        void responseContainsCorrectTransformationMethod() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            Derivative derivative = createTestDerivative(request);
            when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

            // When
            ResponseEntity<DerivativeResponse> response = controller.createDerivative(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().transformationMethod()).isEqualTo(derivative.method().name());
        }

        @Test
        @DisplayName("should return response with non-null createdAt timestamp")
        void responseContainsNonNullCreatedAtTimestamp() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            Derivative derivative = createTestDerivative(request);
            when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

            // When
            ResponseEntity<DerivativeResponse> response = controller.createDerivative(setId, docId, request);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should pass correct command to handler")
        void passesCorrectCommandToHandler() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            Derivative derivative = createTestDerivative(request);
            when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

            // When
            controller.createDerivative(setId, docId, request);

            // Then
            ArgumentCaptor<CreateDerivativeCommand> commandCaptor = 
                ArgumentCaptor.forClass(CreateDerivativeCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            CreateDerivativeCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.documentSetId().value()).isEqualTo(setId);
            assertThat(capturedCommand.documentId().value()).isEqualTo(docId);
            assertThat(capturedCommand.sourceVersionNumber()).isEqualTo(request.sourceVersionNumber());
            assertThat(capturedCommand.targetFormat()).isEqualTo(request.targetFormat());
        }

        @Test
        @DisplayName("should throw DuplicateDerivativeException when duplicate derivative is created")
        void duplicateDerivativeThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            DocumentVersionId sourceVersionId = DocumentVersionId.generate();
            
            when(commandHandler.handle(any(CreateDerivativeCommand.class)))
                .thenThrow(new DuplicateDerivativeException(sourceVersionId, request.targetFormat()));

            // When/Then
            assertThatThrownBy(() -> controller.createDerivative(setId, docId, request))
                .isInstanceOf(DuplicateDerivativeException.class)
                .hasMessageContaining("Derivative already exists");
        }

        @Test
        @DisplayName("should throw DocumentNotFoundException when document not found")
        void documentNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            
            when(commandHandler.handle(any(CreateDerivativeCommand.class)))
                .thenThrow(new DocumentNotFoundException(new DocumentSetId(setId), new DocumentId(docId)));

            // When/Then
            assertThatThrownBy(() -> controller.createDerivative(setId, docId, request))
                .isInstanceOf(DocumentNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DocumentSetNotFoundException when document set not found")
        void documentSetNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            
            when(commandHandler.handle(any(CreateDerivativeCommand.class)))
                .thenThrow(new DocumentSetNotFoundException(new DocumentSetId(setId)));

            // When/Then
            assertThatThrownBy(() -> controller.createDerivative(setId, docId, request))
                .isInstanceOf(DocumentSetNotFoundException.class);
        }

        @Test
        @DisplayName("should throw VersionNotFoundException when source version not found")
        void versionNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            CreateDerivativeRequest request = createValidCreateDerivativeRequest();
            
            when(commandHandler.handle(any(CreateDerivativeCommand.class)))
                .thenThrow(new VersionNotFoundException(
                    new DocumentId(docId), 
                    request.sourceVersionNumber()
                ));

            // When/Then
            assertThatThrownBy(() -> controller.createDerivative(setId, docId, request))
                .isInstanceOf(VersionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("GET /api/document-sets/{setId}/documents/{docId}/derivatives (getDerivatives)")
    class GetDerivativesTests {

        @Test
        @DisplayName("should return 200 OK when document is found")
        void foundReturns200Ok() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return empty list when document has no derivatives")
        void returnsEmptyListWhenNoDerivatives() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should return all derivatives when document has derivatives")
        void returnsAllDerivativesWhenPresent() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            
            // Add derivatives to the document
            DocumentVersion version = document.getCurrentVersion();
            ContentHash derivativeHash1 = Content.computeHash("pdf content".getBytes());
            ContentRef derivativeRef1 = ContentRef.of(derivativeHash1);
            document.addDerivative(version.id(), Format.PDF, derivativeRef1, derivativeHash1, TransformationMethod.PROGRAMMATIC);
            
            ContentHash derivativeHash2 = Content.computeHash("json content".getBytes());
            ContentRef derivativeRef2 = ContentRef.of(derivativeHash2);
            document.addDerivative(version.id(), Format.JSON, derivativeRef2, derivativeHash2, TransformationMethod.PROGRAMMATIC);
            
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("should return derivatives with correct target formats")
        void returnsDerivativesWithCorrectTargetFormats() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            
            DocumentVersion version = document.getCurrentVersion();
            ContentHash derivativeHash1 = Content.computeHash("pdf content".getBytes());
            ContentRef derivativeRef1 = ContentRef.of(derivativeHash1);
            document.addDerivative(version.id(), Format.PDF, derivativeRef1, derivativeHash1, TransformationMethod.PROGRAMMATIC);
            
            ContentHash derivativeHash2 = Content.computeHash("json content".getBytes());
            ContentRef derivativeRef2 = ContentRef.of(derivativeHash2);
            document.addDerivative(version.id(), Format.JSON, derivativeRef2, derivativeHash2, TransformationMethod.PROGRAMMATIC);
            
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody())
                    .extracting(DerivativeResponse::targetFormat)
                    .containsExactlyInAnyOrder(Format.PDF, Format.JSON);
        }

        @Test
        @DisplayName("should return derivatives with correct source version IDs")
        void returnsDerivativesWithCorrectSourceVersionIds() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            
            DocumentVersion version = document.getCurrentVersion();
            ContentHash derivativeHash = Content.computeHash("pdf content".getBytes());
            ContentRef derivativeRef = ContentRef.of(derivativeHash);
            document.addDerivative(version.id(), Format.PDF, derivativeRef, derivativeHash, TransformationMethod.PROGRAMMATIC);
            
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).sourceVersionId()).isEqualTo(version.id().value());
        }

        @Test
        @DisplayName("should return derivatives with non-null IDs")
        void returnsDerivativesWithNonNullIds() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            
            DocumentVersion version = document.getCurrentVersion();
            ContentHash derivativeHash = Content.computeHash("pdf content".getBytes());
            ContentRef derivativeRef = ContentRef.of(derivativeHash);
            document.addDerivative(version.id(), Format.PDF, derivativeRef, derivativeHash, TransformationMethod.PROGRAMMATIC);
            
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).id()).isNotNull();
        }

        @Test
        @DisplayName("should return derivatives with non-null content hashes")
        void returnsDerivativesWithNonNullContentHashes() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            
            DocumentVersion version = document.getCurrentVersion();
            ContentHash derivativeHash = Content.computeHash("pdf content".getBytes());
            ContentRef derivativeRef = ContentRef.of(derivativeHash);
            document.addDerivative(version.id(), Format.PDF, derivativeRef, derivativeHash, TransformationMethod.PROGRAMMATIC);
            
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).contentHash()).isNotNull();
        }

        @Test
        @DisplayName("should return derivatives with non-null transformation methods")
        void returnsDerivativesWithNonNullTransformationMethods() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            
            DocumentVersion version = document.getCurrentVersion();
            ContentHash derivativeHash = Content.computeHash("pdf content".getBytes());
            ContentRef derivativeRef = ContentRef.of(derivativeHash);
            document.addDerivative(version.id(), Format.PDF, derivativeRef, derivativeHash, TransformationMethod.PROGRAMMATIC);
            
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).transformationMethod()).isNotNull();
        }

        @Test
        @DisplayName("should return derivatives with non-null createdAt timestamps")
        void returnsDerivativesWithNonNullCreatedAtTimestamps() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            
            DocumentVersion version = document.getCurrentVersion();
            ContentHash derivativeHash = Content.computeHash("pdf content".getBytes());
            ContentRef derivativeRef = ContentRef.of(derivativeHash);
            document.addDerivative(version.id(), Format.PDF, derivativeRef, derivativeHash, TransformationMethod.PROGRAMMATIC);
            
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).createdAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw DocumentSetNotFoundException when document set not found")
        void documentSetNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> controller.getDerivatives(setId, docId))
                    .isInstanceOf(DocumentSetNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DocumentNotFoundException when document not found within set")
        void documentNotFoundThrowsException() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            UUID nonExistentDocId = UUID.randomUUID();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When/Then
            assertThatThrownBy(() -> controller.getDerivatives(documentSet.id().value(), nonExistentDocId))
                    .isInstanceOf(DocumentNotFoundException.class);
        }

        @Test
        @DisplayName("should query repository with correct DocumentSetId")
        void queriesRepositoryWithCorrectDocumentSetId() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            UUID requestedSetId = documentSet.id().value();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            controller.getDerivatives(requestedSetId, document.id().value());

            // Then
            ArgumentCaptor<DocumentSetId> idCaptor = ArgumentCaptor.forClass(DocumentSetId.class);
            verify(repository).findById(idCaptor.capture());
            assertThat(idCaptor.getValue().value()).isEqualTo(requestedSetId);
        }
    }

    @Nested
    @DisplayName("GET /api/document-sets/{setId}/documents/{docId} (getDocument)")
    class GetDocumentTests {

        @Test
        @DisplayName("should return 200 OK when document is found")
        void foundReturns200Ok() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentResponse> response = controller.getDocument(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return response with correct document ID")
        void responseContainsCorrectDocumentId() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentResponse> response = controller.getDocument(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(document.id().value());
        }

        @Test
        @DisplayName("should return response with correct document type")
        void responseContainsCorrectDocumentType() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentResponse> response = controller.getDocument(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().type()).isEqualTo(document.type());
        }

        @Test
        @DisplayName("should return response with correct schema reference")
        void responseContainsCorrectSchemaReference() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentResponse> response = controller.getDocument(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().schemaRef()).isNotNull();
            assertThat(response.getBody().schemaRef().schemaId())
                    .isEqualTo(document.schemaRef().schemaId().value());
            assertThat(response.getBody().schemaRef().version())
                    .isEqualTo(document.schemaRef().version().value());
        }

        @Test
        @DisplayName("should return response with correct version count")
        void responseContainsCorrectVersionCount() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentResponse> response = controller.getDocument(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().versionCount()).isEqualTo(document.versionCount());
        }

        @Test
        @DisplayName("should return response with current version details")
        void responseContainsCurrentVersionDetails() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentResponse> response = controller.getDocument(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().currentVersion()).isNotNull();
            
            var currentVersion = document.getCurrentVersion();
            assertThat(response.getBody().currentVersion().id())
                    .isEqualTo(currentVersion.id().value());
            assertThat(response.getBody().currentVersion().versionNumber())
                    .isEqualTo(currentVersion.versionNumber());
            assertThat(response.getBody().currentVersion().createdBy())
                    .isEqualTo(currentVersion.createdBy());
            assertThat(response.getBody().currentVersion().createdAt())
                    .isEqualTo(currentVersion.createdAt());
        }

        @Test
        @DisplayName("should return response with empty derivatives list for new document")
        void responseContainsEmptyDerivativesListForNewDocument() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            ResponseEntity<DocumentResponse> response = controller.getDocument(
                    documentSet.id().value(),
                    document.id().value()
            );

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().derivatives()).isEmpty();
        }

        @Test
        @DisplayName("should throw DocumentSetNotFoundException when document set not found")
        void documentSetNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> controller.getDocument(setId, docId))
                    .isInstanceOf(DocumentSetNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DocumentNotFoundException when document not found within set")
        void documentNotFoundThrowsException() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            UUID nonExistentDocId = UUID.randomUUID();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When/Then
            assertThatThrownBy(() -> controller.getDocument(documentSet.id().value(), nonExistentDocId))
                    .isInstanceOf(DocumentNotFoundException.class);
        }

        @Test
        @DisplayName("should query repository with correct DocumentSetId")
        void queriesRepositoryWithCorrectDocumentSetId() {
            // Given
            DocumentSet documentSet = createTestDocumentSetWithDefaults();
            Document document = documentSet.getAllDocuments().get(0);
            UUID requestedSetId = documentSet.id().value();
            when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

            // When
            controller.getDocument(requestedSetId, document.id().value());

            // Then
            ArgumentCaptor<DocumentSetId> idCaptor = ArgumentCaptor.forClass(DocumentSetId.class);
            verify(repository).findById(idCaptor.capture());
            assertThat(idCaptor.getValue().value()).isEqualTo(requestedSetId);
        }
    }

    // Helper methods for AddDocumentTests

    private AddDocumentRequest createValidAddDocumentRequest() {
        return new AddDocumentRequest(
            DocumentType.ORDER,
            UUID.randomUUID(),
            "1.0.0",
            Base64.getEncoder().encodeToString("test document content".getBytes()),
            "user@example.com",
            null
        );
    }

    private Document createTestDocument(AddDocumentRequest request) {
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId(request.schemaId()),
            VersionIdentifier.of(request.schemaVersion())
        );
        
        byte[] contentBytes = Base64.getDecoder().decode(request.content());
        ContentHash contentHash = Content.computeHash(contentBytes);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        return Document.create(
            request.documentType(),
            schemaRef,
            contentRef,
            contentHash,
            request.createdBy()
        );
    }

    // Helper methods for AddVersionTests

    private AddVersionRequest createValidAddVersionRequest() {
        return new AddVersionRequest(
            Base64.getEncoder().encodeToString("updated document content".getBytes()),
            "user@example.com"
        );
    }

    private DocumentVersion createTestDocumentVersion(AddVersionRequest request, int versionNumber) {
        byte[] contentBytes = Base64.getDecoder().decode(request.content());
        ContentHash contentHash = Content.computeHash(contentBytes);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        // Create a first version to use as previous version for subsequent versions
        if (versionNumber == 1) {
            return DocumentVersion.createFirst(contentRef, contentHash, request.createdBy());
        }
        
        // For version > 1, create a previous version first
        DocumentVersion previousVersion = DocumentVersion.createFirst(
            contentRef, 
            contentHash, 
            request.createdBy()
        );
        
        // Create subsequent versions up to the requested version number
        DocumentVersion currentVersion = previousVersion;
        for (int i = 2; i <= versionNumber; i++) {
            currentVersion = DocumentVersion.createNext(currentVersion, contentRef, contentHash, request.createdBy());
        }
        
        return currentVersion;
    }

    // Helper methods

    private CreateDocumentSetRequest createValidRequest() {
        return new CreateDocumentSetRequest(
            DocumentType.INVOICE,
            UUID.randomUUID(),
            "1.0.0",
            Base64.getEncoder().encodeToString("test content".getBytes()),
            "user@example.com",
            Map.of("department", "finance")
        );
    }

    private CreateDocumentSetRequest createValidRequestWithMetadata(Map<String, String> metadata) {
        return new CreateDocumentSetRequest(
            DocumentType.INVOICE,
            UUID.randomUUID(),
            "1.0.0",
            Base64.getEncoder().encodeToString("test content".getBytes()),
            "user@example.com",
            metadata
        );
    }

    private DocumentSet createTestDocumentSet(CreateDocumentSetRequest request) {
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId(request.schemaId()),
            VersionIdentifier.of(request.schemaVersion())
        );
        
        ContentHash contentHash = Content.computeHash(
            Base64.getDecoder().decode(request.content())
        );
        ContentRef contentRef = ContentRef.of(contentHash);
        
        return DocumentSet.createWithDocument(
            request.documentType(),
            schemaRef,
            contentRef,
            contentHash,
            request.createdBy(),
            request.metadata()
        );
    }

    private DocumentSet createTestDocumentSetWithDefaults() {
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId(UUID.randomUUID()),
            VersionIdentifier.of("1.0.0")
        );
        
        byte[] contentBytes = "test content".getBytes();
        ContentHash contentHash = Content.computeHash(contentBytes);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        return DocumentSet.createWithDocument(
            DocumentType.INVOICE,
            schemaRef,
            contentRef,
            contentHash,
            "user@example.com",
            Map.of("department", "finance")
        );
    }

    private DocumentSet createTestDocumentSetWithMetadata(Map<String, String> metadata) {
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId(UUID.randomUUID()),
            VersionIdentifier.of("1.0.0")
        );
        
        byte[] contentBytes = "test content".getBytes();
        ContentHash contentHash = Content.computeHash(contentBytes);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        return DocumentSet.createWithDocument(
            DocumentType.INVOICE,
            schemaRef,
            contentRef,
            contentHash,
            "user@example.com",
            metadata
        );
    }

    /**
     * Unit tests for POST /api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/validate (validateDocument).
     *
     * <p>Tests the validateDocument endpoint behaviour including request mapping,
     * command construction, response mapping, and HTTP status code handling.</p>
     *
     * <p><b>Validates: Requirements 5.1, 5.2, 5.3, 5.4</b></p>
     */
    @Nested
    @DisplayName("POST /api/document-sets/{setId}/documents/{docId}/versions/{versionNumber}/validate (validateDocument)")
    class ValidateDocumentTests {

        @Test
        @DisplayName("should return 200 OK when document is valid")
        void validDocumentReturns200Ok() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult validResult = ValidationResult.success();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(validResult);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("should return response with valid=true when document is valid")
        void validDocumentReturnsValidTrue() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult validResult = ValidationResult.success();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(validResult);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().valid()).isTrue();
        }

        @Test
        @DisplayName("should return response with empty errors list when document is valid")
        void validDocumentReturnsEmptyErrorsList() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult validResult = ValidationResult.success();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(validResult);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errors()).isEmpty();
        }

        @Test
        @DisplayName("should return response with empty warnings list when document is valid with no warnings")
        void validDocumentReturnsEmptyWarningsList() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult validResult = ValidationResult.success();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(validResult);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().warnings()).isEmpty();
        }

        @Test
        @DisplayName("should return 422 Unprocessable Entity when document is invalid")
        void invalidDocumentReturns422UnprocessableEntity() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult invalidResult = createInvalidValidationResult();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(invalidResult);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("should return response with valid=false when document is invalid")
        void invalidDocumentReturnsValidFalse() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult invalidResult = createInvalidValidationResult();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(invalidResult);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().valid()).isFalse();
        }

        @Test
        @DisplayName("should return response with validation errors when document is invalid")
        void invalidDocumentReturnsValidationErrors() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult invalidResult = createInvalidValidationResult();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(invalidResult);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errors()).isNotEmpty();
            assertThat(response.getBody().errors()).hasSize(2);
        }

        @Test
        @DisplayName("should return response with correct error paths and messages")
        void invalidDocumentReturnsCorrectErrorDetails() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult invalidResult = createInvalidValidationResult();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(invalidResult);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errors())
                .extracting(com.example.documents.api.dto.ValidationResultResponse.ValidationErrorResponse::path)
                .containsExactlyInAnyOrder("/Invoice/ID", "/Invoice/IssueDate");
            assertThat(response.getBody().errors())
                .extracting(com.example.documents.api.dto.ValidationResultResponse.ValidationErrorResponse::message)
                .containsExactlyInAnyOrder("ID is required", "IssueDate format is invalid");
        }

        @Test
        @DisplayName("should return response with warnings when validation has warnings")
        void validationReturnsWarnings() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult resultWithWarnings = createValidationResultWithWarnings();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(resultWithWarnings);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().warnings()).isNotEmpty();
            assertThat(response.getBody().warnings()).hasSize(1);
        }

        @Test
        @DisplayName("should return response with correct warning paths and messages")
        void validationReturnsCorrectWarningDetails() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            ValidationResult resultWithWarnings = createValidationResultWithWarnings();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(resultWithWarnings);

            // When
            ResponseEntity<com.example.documents.api.dto.ValidationResultResponse> response = 
                controller.validateDocument(setId, docId, versionNumber);

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().warnings())
                .extracting(com.example.documents.api.dto.ValidationResultResponse.ValidationWarningResponse::path)
                .containsExactly("/Invoice/Note");
            assertThat(response.getBody().warnings())
                .extracting(com.example.documents.api.dto.ValidationResultResponse.ValidationWarningResponse::message)
                .containsExactly("Note field is deprecated");
        }

        @Test
        @DisplayName("should pass correct command to handler")
        void passesCorrectCommandToHandler() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 2;
            ValidationResult validResult = ValidationResult.success();
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenReturn(validResult);

            // When
            controller.validateDocument(setId, docId, versionNumber);

            // Then
            ArgumentCaptor<com.example.documents.application.command.ValidateDocumentCommand> commandCaptor = 
                ArgumentCaptor.forClass(com.example.documents.application.command.ValidateDocumentCommand.class);
            verify(commandHandler).handle(commandCaptor.capture());
            
            com.example.documents.application.command.ValidateDocumentCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.documentSetId().value()).isEqualTo(setId);
            assertThat(capturedCommand.documentId().value()).isEqualTo(docId);
            assertThat(capturedCommand.versionNumber()).isEqualTo(versionNumber);
        }

        @Test
        @DisplayName("should throw DocumentSetNotFoundException when document set not found")
        void documentSetNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenThrow(new DocumentSetNotFoundException(new DocumentSetId(setId)));

            // When/Then
            assertThatThrownBy(() -> controller.validateDocument(setId, docId, versionNumber))
                .isInstanceOf(DocumentSetNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DocumentNotFoundException when document not found")
        void documentNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 1;
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenThrow(new DocumentNotFoundException(new DocumentSetId(setId), new DocumentId(docId)));

            // When/Then
            assertThatThrownBy(() -> controller.validateDocument(setId, docId, versionNumber))
                .isInstanceOf(DocumentNotFoundException.class);
        }

        @Test
        @DisplayName("should throw VersionNotFoundException when version not found")
        void versionNotFoundThrowsException() {
            // Given
            UUID setId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();
            int versionNumber = 999;
            when(commandHandler.handle(any(com.example.documents.application.command.ValidateDocumentCommand.class)))
                .thenThrow(new VersionNotFoundException(new DocumentId(docId), versionNumber));

            // When/Then
            assertThatThrownBy(() -> controller.validateDocument(setId, docId, versionNumber))
                .isInstanceOf(VersionNotFoundException.class);
        }

        // Helper methods for ValidateDocumentTests

        private ValidationResult createInvalidValidationResult() {
            List<com.example.documents.domain.model.ValidationError> errors = List.of(
                com.example.documents.domain.model.ValidationError.of("/Invoice/ID", "ID is required", "REQUIRED_FIELD"),
                com.example.documents.domain.model.ValidationError.of("/Invoice/IssueDate", "IssueDate format is invalid", "INVALID_FORMAT")
            );
            return ValidationResult.failure(errors);
        }

        private ValidationResult createValidationResultWithWarnings() {
            List<com.example.documents.domain.model.ValidationWarning> warnings = List.of(
                com.example.documents.domain.model.ValidationWarning.of("/Invoice/Note", "Note field is deprecated")
            );
            return ValidationResult.successWithWarnings(warnings);
        }
    }

    // Helper methods for CreateDerivativeTests

    private CreateDerivativeRequest createValidCreateDerivativeRequest() {
        return new CreateDerivativeRequest(1, Format.PDF);
    }

    private Derivative createTestDerivative(CreateDerivativeRequest request) {
        DocumentVersionId sourceVersionId = DocumentVersionId.generate();
        ContentHash contentHash = Content.computeHash("derivative content".getBytes());
        ContentRef contentRef = ContentRef.of(contentHash);
        
        return Derivative.create(
            sourceVersionId,
            request.targetFormat(),
            contentRef,
            contentHash,
            TransformationMethod.PROGRAMMATIC
        );
    }
}
