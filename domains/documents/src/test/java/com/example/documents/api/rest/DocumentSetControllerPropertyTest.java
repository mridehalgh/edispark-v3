package com.example.documents.api.rest;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.documents.api.dto.AddDocumentRequest;
import com.example.documents.api.dto.AddVersionRequest;
import com.example.documents.api.dto.CreateDerivativeRequest;
import com.example.documents.api.dto.CreateDocumentSetRequest;
import com.example.documents.api.dto.DocumentResponse;
import com.example.documents.api.dto.DocumentSetResponse;
import com.example.documents.api.dto.DocumentVersionResponse;
import com.example.documents.application.command.AddDocumentCommand;
import com.example.documents.application.command.AddVersionCommand;
import com.example.documents.application.command.CreateDerivativeCommand;
import com.example.documents.application.command.CreateDocumentSetCommand;
import com.example.documents.application.handler.DocumentSetCommandHandler;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.Derivative;
import com.example.documents.domain.model.Document;
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
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.DocumentSetRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for {@link DocumentSetController}.
 *
 * <p><b>Property 1: Valid Create Requests Return 201 Created</b></p>
 * <p>For any valid create request (document set or document), the API SHALL return HTTP 201 Created
 * with a response body containing the created resource's ID.</p>
 *
 * <p><b>Property 2: Create-Retrieve Round Trip</b></p>
 * <p>For any document set created via POST request, a subsequent GET request for that
 * resource SHALL return data equivalent to what was created (ID matches, required fields
 * present, values correspond to input).</p>
 *
 * <p><b>Validates: Requirements 1.1, 1.3, 2.1</b></p>
 */
class DocumentSetControllerPropertyTest {

    /**
     * Property 1: Valid Create Requests Return 201 Created
     *
     * <p>For any valid CreateDocumentSetRequest, the controller SHALL return
     * HTTP 201 Created status.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid create requests return 201 Created")
    void validCreateRequestsReturn201Created(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

        // When
        ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

        // Then
        assertThat(response.getStatusCode())
            .as("Valid request should return 201 Created for request with documentType=%s, createdBy=%s",
                request.documentType(), request.createdBy())
            .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Property 1: Valid Create Requests Return Response with ID
     *
     * <p>For any valid CreateDocumentSetRequest, the response body SHALL contain
     * a non-null document set ID.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid create requests return response with ID")
    void validCreateRequestsReturnResponseWithId(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

        // When
        ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

        // Then
        assertThat(response.getBody())
            .as("Response body should not be null")
            .isNotNull();
        assertThat(response.getBody().id())
            .as("Response should contain a non-null document set ID")
            .isNotNull();
    }

    /**
     * Property 1: Valid Create Requests Return Response with Matching CreatedBy
     *
     * <p>For any valid CreateDocumentSetRequest, the response body SHALL contain
     * the same createdBy value as the request.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid create requests return response with matching createdBy")
    void validCreateRequestsReturnResponseWithMatchingCreatedBy(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

        // When
        ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().createdBy())
            .as("Response createdBy should match request createdBy")
            .isEqualTo(request.createdBy());
    }

    /**
     * Property 1: Valid Create Requests Return Response with Document Summaries
     *
     * <p>For any valid CreateDocumentSetRequest, the response body SHALL contain
     * at least one document summary (the initial document).</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid create requests return response with document summaries")
    void validCreateRequestsReturnResponseWithDocumentSummaries(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

        // When
        ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().documents())
            .as("Response should contain at least one document summary")
            .isNotEmpty();
        assertThat(response.getBody().documents().get(0).type())
            .as("Document summary type should match request document type")
            .isEqualTo(request.documentType());
    }

    /**
     * Property 1: Valid Create Requests Return Response with Non-Null Timestamp
     *
     * <p>For any valid CreateDocumentSetRequest, the response body SHALL contain
     * a non-null createdAt timestamp.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid create requests return response with non-null timestamp")
    void validCreateRequestsReturnResponseWithNonNullTimestamp(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);

        // When
        ResponseEntity<DocumentSetResponse> response = controller.createDocumentSet(request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().createdAt())
            .as("Response should contain a non-null createdAt timestamp")
            .isNotNull();
    }

    // ========================================================================
    // Property 2: Create-Retrieve Round Trip
    // ========================================================================

    /**
     * Property 2: Create-Retrieve Round Trip - ID Matches
     *
     * <p>For any document set created via POST, a subsequent GET with the same ID
     * SHALL return a response with the same document set ID.</p>
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip - ID matches")
    void createRetrieveRoundTripIdMatches(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentSetResponse> createResponse = controller.createDocumentSet(request);
        assertThat(createResponse.getBody()).isNotNull();
        UUID createdId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentSetResponse> getResponse = controller.getDocumentSet(createdId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().id())
            .as("Retrieved document set ID should match created ID")
            .isEqualTo(createdId);
    }

    /**
     * Property 2: Create-Retrieve Round Trip - CreatedBy Matches
     *
     * <p>For any document set created via POST, a subsequent GET SHALL return
     * a response with the same createdBy value.</p>
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip - createdBy matches")
    void createRetrieveRoundTripCreatedByMatches(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentSetResponse> createResponse = controller.createDocumentSet(request);
        assertThat(createResponse.getBody()).isNotNull();
        String createdBy = createResponse.getBody().createdBy();
        UUID createdId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentSetResponse> getResponse = controller.getDocumentSet(createdId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().createdBy())
            .as("Retrieved createdBy should match created createdBy")
            .isEqualTo(createdBy);
    }

    /**
     * Property 2: Create-Retrieve Round Trip - CreatedAt Matches
     *
     * <p>For any document set created via POST, a subsequent GET SHALL return
     * a response with the same createdAt timestamp.</p>
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip - createdAt matches")
    void createRetrieveRoundTripCreatedAtMatches(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentSetResponse> createResponse = controller.createDocumentSet(request);
        assertThat(createResponse.getBody()).isNotNull();
        var createdAt = createResponse.getBody().createdAt();
        UUID createdId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentSetResponse> getResponse = controller.getDocumentSet(createdId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().createdAt())
            .as("Retrieved createdAt should match created createdAt")
            .isEqualTo(createdAt);
    }

    /**
     * Property 2: Create-Retrieve Round Trip - Metadata Matches
     *
     * <p>For any document set created via POST, a subsequent GET SHALL return
     * a response with equivalent metadata.</p>
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip - metadata matches")
    void createRetrieveRoundTripMetadataMatches(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentSetResponse> createResponse = controller.createDocumentSet(request);
        assertThat(createResponse.getBody()).isNotNull();
        var createdMetadata = createResponse.getBody().metadata();
        UUID createdId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentSetResponse> getResponse = controller.getDocumentSet(createdId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().metadata())
            .as("Retrieved metadata should match created metadata")
            .isEqualTo(createdMetadata);
    }

    /**
     * Property 2: Create-Retrieve Round Trip - Document Summaries Match
     *
     * <p>For any document set created via POST, a subsequent GET SHALL return
     * a response with equivalent document summaries (same count, types, and version counts).</p>
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip - document summaries match")
    void createRetrieveRoundTripDocumentSummariesMatch(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentSetResponse> createResponse = controller.createDocumentSet(request);
        assertThat(createResponse.getBody()).isNotNull();
        var createdDocuments = createResponse.getBody().documents();
        UUID createdId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentSetResponse> getResponse = controller.getDocumentSet(createdId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().documents())
            .as("Retrieved documents should have same size as created documents")
            .hasSameSizeAs(createdDocuments);
        
        // Verify each document summary matches
        for (int i = 0; i < createdDocuments.size(); i++) {
            var created = createdDocuments.get(i);
            var retrieved = getResponse.getBody().documents().get(i);
            
            assertThat(retrieved.id())
                .as("Document %d ID should match", i)
                .isEqualTo(created.id());
            assertThat(retrieved.type())
                .as("Document %d type should match", i)
                .isEqualTo(created.type());
            assertThat(retrieved.versionCount())
                .as("Document %d version count should match", i)
                .isEqualTo(created.versionCount());
        }
    }

    /**
     * Property 2: Create-Retrieve Round Trip - GET Returns 200 OK
     *
     * <p>For any document set created via POST, a subsequent GET SHALL return
     * HTTP 200 OK status.</p>
     *
     * <p><b>Validates: Requirements 1.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip - GET returns 200 OK")
    void createRetrieveRoundTripGetReturns200Ok(
            @ForAll("validCreateDocumentSetRequests") CreateDocumentSetRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        DocumentSet documentSet = createDocumentSetFromRequest(request);
        when(commandHandler.handle(any(CreateDocumentSetCommand.class))).thenReturn(documentSet);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentSetResponse> createResponse = controller.createDocumentSet(request);
        assertThat(createResponse.getBody()).isNotNull();
        UUID createdId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentSetResponse> getResponse = controller.getDocumentSet(createdId);

        // Then
        assertThat(getResponse.getStatusCode())
            .as("GET request for created document set should return 200 OK")
            .isEqualTo(HttpStatus.OK);
    }

    // ========================================================================
    // Property 1: Valid Add Document Requests Return 201 Created
    // ========================================================================

    /**
     * Property 1: Valid Add Document Requests Return 201 Created
     *
     * <p>For any valid AddDocumentRequest, the controller SHALL return
     * HTTP 201 Created status.</p>
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add document requests return 201 Created")
    void validAddDocumentRequestsReturn201Created(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

        // When
        ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

        // Then
        assertThat(response.getStatusCode())
            .as("Valid request should return 201 Created for request with documentType=%s, createdBy=%s",
                request.documentType(), request.createdBy())
            .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Property 1: Valid Add Document Requests Return Response with ID
     *
     * <p>For any valid AddDocumentRequest, the response body SHALL contain
     * a non-null document ID.</p>
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add document requests return response with ID")
    void validAddDocumentRequestsReturnResponseWithId(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

        // When
        ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

        // Then
        assertThat(response.getBody())
            .as("Response body should not be null")
            .isNotNull();
        assertThat(response.getBody().id())
            .as("Response should contain a non-null document ID")
            .isNotNull();
    }

    /**
     * Property 1: Valid Add Document Requests Return Response with Matching Type
     *
     * <p>For any valid AddDocumentRequest, the response body SHALL contain
     * the same document type as the request.</p>
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add document requests return response with matching type")
    void validAddDocumentRequestsReturnResponseWithMatchingType(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

        // When
        ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().type())
            .as("Response document type should match request document type")
            .isEqualTo(request.documentType());
    }

    /**
     * Property 1: Valid Add Document Requests Return Response with Schema Reference
     *
     * <p>For any valid AddDocumentRequest, the response body SHALL contain
     * a schema reference matching the request.</p>
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add document requests return response with schema reference")
    void validAddDocumentRequestsReturnResponseWithSchemaReference(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

        // When
        ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().schemaRef())
            .as("Response should contain a non-null schema reference")
            .isNotNull();
        assertThat(response.getBody().schemaRef().schemaId())
            .as("Schema ID should match request")
            .isEqualTo(request.schemaId());
        assertThat(response.getBody().schemaRef().version())
            .as("Schema version should match request")
            .isEqualTo(request.schemaVersion());
    }

    /**
     * Property 1: Valid Add Document Requests Return Response with Current Version
     *
     * <p>For any valid AddDocumentRequest, the response body SHALL contain
     * a current version with version number 1.</p>
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add document requests return response with current version")
    void validAddDocumentRequestsReturnResponseWithCurrentVersion(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);

        // When
        ResponseEntity<DocumentResponse> response = controller.addDocument(setId, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currentVersion())
            .as("Response should contain a non-null current version")
            .isNotNull();
        assertThat(response.getBody().currentVersion().versionNumber())
            .as("New document should have version number 1")
            .isEqualTo(1);
        assertThat(response.getBody().currentVersion().createdBy())
            .as("Version createdBy should match request")
            .isEqualTo(request.createdBy());
    }

    // ========================================================================
    // Property 2: Create-Retrieve Round Trip for Documents
    // ========================================================================

    /**
     * Property 2: Create-Retrieve Round Trip for Documents - ID Matches
     *
     * <p>For any document added via POST, a subsequent GET with the same IDs
     * SHALL return a response with the same document ID.</p>
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for documents - ID matches")
    void createRetrieveRoundTripForDocumentsIdMatches(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        // Create a document set first
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        DocumentSet documentSet = createDocumentSetWithDocument(document);
        
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentResponse> createResponse = controller.addDocument(setId, request);
        assertThat(createResponse.getBody()).isNotNull();
        UUID createdDocId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentResponse> getResponse = controller.getDocument(setId, createdDocId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().id())
            .as("Retrieved document ID should match created ID")
            .isEqualTo(createdDocId);
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Documents - Type Matches
     *
     * <p>For any document added via POST, a subsequent GET SHALL return
     * a response with the same document type.</p>
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for documents - type matches")
    void createRetrieveRoundTripForDocumentsTypeMatches(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        DocumentSet documentSet = createDocumentSetWithDocument(document);
        
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentResponse> createResponse = controller.addDocument(setId, request);
        assertThat(createResponse.getBody()).isNotNull();
        DocumentType createdType = createResponse.getBody().type();
        UUID createdDocId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentResponse> getResponse = controller.getDocument(setId, createdDocId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().type())
            .as("Retrieved document type should match created type")
            .isEqualTo(createdType);
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Documents - Schema Reference Matches
     *
     * <p>For any document added via POST, a subsequent GET SHALL return
     * a response with the same schema reference.</p>
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for documents - schema reference matches")
    void createRetrieveRoundTripForDocumentsSchemaRefMatches(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        DocumentSet documentSet = createDocumentSetWithDocument(document);
        
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentResponse> createResponse = controller.addDocument(setId, request);
        assertThat(createResponse.getBody()).isNotNull();
        var createdSchemaRef = createResponse.getBody().schemaRef();
        UUID createdDocId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentResponse> getResponse = controller.getDocument(setId, createdDocId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().schemaRef())
            .as("Retrieved schema reference should not be null")
            .isNotNull();
        assertThat(getResponse.getBody().schemaRef().schemaId())
            .as("Retrieved schema ID should match created schema ID")
            .isEqualTo(createdSchemaRef.schemaId());
        assertThat(getResponse.getBody().schemaRef().version())
            .as("Retrieved schema version should match created schema version")
            .isEqualTo(createdSchemaRef.version());
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Documents - Version Count Matches
     *
     * <p>For any document added via POST, a subsequent GET SHALL return
     * a response with the same version count.</p>
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for documents - version count matches")
    void createRetrieveRoundTripForDocumentsVersionCountMatches(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        DocumentSet documentSet = createDocumentSetWithDocument(document);
        
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentResponse> createResponse = controller.addDocument(setId, request);
        assertThat(createResponse.getBody()).isNotNull();
        int createdVersionCount = createResponse.getBody().versionCount();
        UUID createdDocId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentResponse> getResponse = controller.getDocument(setId, createdDocId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().versionCount())
            .as("Retrieved version count should match created version count")
            .isEqualTo(createdVersionCount);
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Documents - Current Version Matches
     *
     * <p>For any document added via POST, a subsequent GET SHALL return
     * a response with equivalent current version details.</p>
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for documents - current version matches")
    void createRetrieveRoundTripForDocumentsCurrentVersionMatches(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        DocumentSet documentSet = createDocumentSetWithDocument(document);
        
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentResponse> createResponse = controller.addDocument(setId, request);
        assertThat(createResponse.getBody()).isNotNull();
        var createdCurrentVersion = createResponse.getBody().currentVersion();
        UUID createdDocId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentResponse> getResponse = controller.getDocument(setId, createdDocId);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().currentVersion())
            .as("Retrieved current version should not be null")
            .isNotNull();
        assertThat(getResponse.getBody().currentVersion().id())
            .as("Retrieved version ID should match created version ID")
            .isEqualTo(createdCurrentVersion.id());
        assertThat(getResponse.getBody().currentVersion().versionNumber())
            .as("Retrieved version number should match created version number")
            .isEqualTo(createdCurrentVersion.versionNumber());
        assertThat(getResponse.getBody().currentVersion().createdBy())
            .as("Retrieved version createdBy should match created version createdBy")
            .isEqualTo(createdCurrentVersion.createdBy());
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Documents - GET Returns 200 OK
     *
     * <p>For any document added via POST, a subsequent GET SHALL return
     * HTTP 200 OK status.</p>
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for documents - GET returns 200 OK")
    void createRetrieveRoundTripForDocumentsGetReturns200Ok(
            @ForAll("validAddDocumentRequests") AddDocumentRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        Document document = createDocumentFromRequest(request);
        DocumentSet documentSet = createDocumentSetWithDocument(document);
        
        when(commandHandler.handle(any(AddDocumentCommand.class))).thenReturn(document);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentResponse> createResponse = controller.addDocument(setId, request);
        assertThat(createResponse.getBody()).isNotNull();
        UUID createdDocId = createResponse.getBody().id();

        // When - Retrieve
        ResponseEntity<DocumentResponse> getResponse = controller.getDocument(setId, createdDocId);

        // Then
        assertThat(getResponse.getStatusCode())
            .as("GET request for created document should return 200 OK")
            .isEqualTo(HttpStatus.OK);
    }

    // ========================================================================
    // Property 1: Valid Add Version Requests Return 201 Created
    // ========================================================================

    /**
     * Property 1: Valid Add Version Requests Return 201 Created
     *
     * <p>For any valid AddVersionRequest, the controller SHALL return
     * HTTP 201 Created status.</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add version requests return 201 Created")
    void validAddVersionRequestsReturn201Created(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

        // When
        ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

        // Then
        assertThat(response.getStatusCode())
            .as("Valid request should return 201 Created for request with createdBy=%s",
                request.createdBy())
            .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * Property 1: Valid Add Version Requests Return Response with ID
     *
     * <p>For any valid AddVersionRequest, the response body SHALL contain
     * a non-null version ID.</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add version requests return response with ID")
    void validAddVersionRequestsReturnResponseWithId(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

        // When
        ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

        // Then
        assertThat(response.getBody())
            .as("Response body should not be null")
            .isNotNull();
        assertThat(response.getBody().id())
            .as("Response should contain a non-null version ID")
            .isNotNull();
    }

    /**
     * Property 1: Valid Add Version Requests Return Response with Version Number
     *
     * <p>For any valid AddVersionRequest, the response body SHALL contain
     * a positive version number.</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add version requests return response with version number")
    void validAddVersionRequestsReturnResponseWithVersionNumber(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

        // When
        ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().versionNumber())
            .as("Response should contain a positive version number")
            .isGreaterThan(0);
    }

    /**
     * Property 1: Valid Add Version Requests Return Response with Content Hash
     *
     * <p>For any valid AddVersionRequest, the response body SHALL contain
     * a non-null content hash.</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add version requests return response with content hash")
    void validAddVersionRequestsReturnResponseWithContentHash(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

        // When
        ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentHash())
            .as("Response should contain a non-null content hash")
            .isNotNull()
            .isNotBlank();
    }

    /**
     * Property 1: Valid Add Version Requests Return Response with Matching CreatedBy
     *
     * <p>For any valid AddVersionRequest, the response body SHALL contain
     * the same createdBy value as the request.</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add version requests return response with matching createdBy")
    void validAddVersionRequestsReturnResponseWithMatchingCreatedBy(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

        // When
        ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().createdBy())
            .as("Response createdBy should match request createdBy")
            .isEqualTo(request.createdBy());
    }

    /**
     * Property 1: Valid Add Version Requests Return Response with Non-Null Timestamp
     *
     * <p>For any valid AddVersionRequest, the response body SHALL contain
     * a non-null createdAt timestamp.</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 1: Valid add version requests return response with non-null timestamp")
    void validAddVersionRequestsReturnResponseWithNonNullTimestamp(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);

        // When
        ResponseEntity<DocumentVersionResponse> response = controller.addVersion(setId, docId, request);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().createdAt())
            .as("Response should contain a non-null createdAt timestamp")
            .isNotNull();
    }

    // ========================================================================
    // Property 2: Create-Retrieve Round Trip for Versions
    // ========================================================================

    /**
     * Property 2: Create-Retrieve Round Trip for Versions - ID Matches
     *
     * <p>For any version added via POST, a subsequent GET with the same IDs
     * SHALL return a response with the same version ID.</p>
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for versions - ID matches")
    void createRetrieveRoundTripForVersionsIdMatches(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        // Create a document set with a document that has the version
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentVersionResponse> createResponse = controller.addVersion(setId, docId, request);
        assertThat(createResponse.getBody()).isNotNull();
        UUID createdVersionId = createResponse.getBody().id();
        int createdVersionNumber = createResponse.getBody().versionNumber();

        // When - Retrieve
        ResponseEntity<DocumentVersionResponse> getResponse = controller.getVersion(setId, docId, createdVersionNumber);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().id())
            .as("Retrieved version ID should match created ID")
            .isEqualTo(createdVersionId);
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Versions - Version Number Matches
     *
     * <p>For any version added via POST, a subsequent GET SHALL return
     * a response with the same version number.</p>
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for versions - version number matches")
    void createRetrieveRoundTripForVersionsVersionNumberMatches(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentVersionResponse> createResponse = controller.addVersion(setId, docId, request);
        assertThat(createResponse.getBody()).isNotNull();
        int createdVersionNumber = createResponse.getBody().versionNumber();

        // When - Retrieve
        ResponseEntity<DocumentVersionResponse> getResponse = controller.getVersion(setId, docId, createdVersionNumber);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().versionNumber())
            .as("Retrieved version number should match created version number")
            .isEqualTo(createdVersionNumber);
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Versions - Content Hash Matches
     *
     * <p>For any version added via POST, a subsequent GET SHALL return
     * a response with the same content hash.</p>
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for versions - content hash matches")
    void createRetrieveRoundTripForVersionsContentHashMatches(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentVersionResponse> createResponse = controller.addVersion(setId, docId, request);
        assertThat(createResponse.getBody()).isNotNull();
        String createdContentHash = createResponse.getBody().contentHash();
        int createdVersionNumber = createResponse.getBody().versionNumber();

        // When - Retrieve
        ResponseEntity<DocumentVersionResponse> getResponse = controller.getVersion(setId, docId, createdVersionNumber);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().contentHash())
            .as("Retrieved content hash should match created content hash")
            .isEqualTo(createdContentHash);
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Versions - CreatedBy Matches
     *
     * <p>For any version added via POST, a subsequent GET SHALL return
     * a response with the same createdBy value.</p>
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for versions - createdBy matches")
    void createRetrieveRoundTripForVersionsCreatedByMatches(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentVersionResponse> createResponse = controller.addVersion(setId, docId, request);
        assertThat(createResponse.getBody()).isNotNull();
        String createdBy = createResponse.getBody().createdBy();
        int createdVersionNumber = createResponse.getBody().versionNumber();

        // When - Retrieve
        ResponseEntity<DocumentVersionResponse> getResponse = controller.getVersion(setId, docId, createdVersionNumber);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().createdBy())
            .as("Retrieved createdBy should match created createdBy")
            .isEqualTo(createdBy);
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Versions - CreatedAt Matches
     *
     * <p>For any version added via POST, a subsequent GET SHALL return
     * a response with the same createdAt timestamp.</p>
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for versions - createdAt matches")
    void createRetrieveRoundTripForVersionsCreatedAtMatches(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentVersionResponse> createResponse = controller.addVersion(setId, docId, request);
        assertThat(createResponse.getBody()).isNotNull();
        var createdAt = createResponse.getBody().createdAt();
        int createdVersionNumber = createResponse.getBody().versionNumber();

        // When - Retrieve
        ResponseEntity<DocumentVersionResponse> getResponse = controller.getVersion(setId, docId, createdVersionNumber);

        // Then
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().createdAt())
            .as("Retrieved createdAt should match created createdAt")
            .isEqualTo(createdAt);
    }

    /**
     * Property 2: Create-Retrieve Round Trip for Versions - GET Returns 200 OK
     *
     * <p>For any version added via POST, a subsequent GET SHALL return
     * HTTP 200 OK status.</p>
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 2: Create-Retrieve round trip for versions - GET returns 200 OK")
    void createRetrieveRoundTripForVersionsGetReturns200Ok(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersion version = createDocumentVersionFromRequest(request, 2);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        when(commandHandler.handle(any(AddVersionCommand.class))).thenReturn(version);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When - Create
        ResponseEntity<DocumentVersionResponse> createResponse = controller.addVersion(setId, docId, request);
        assertThat(createResponse.getBody()).isNotNull();
        int createdVersionNumber = createResponse.getBody().versionNumber();

        // When - Retrieve
        ResponseEntity<DocumentVersionResponse> getResponse = controller.getVersion(setId, docId, createdVersionNumber);

        // Then
        assertThat(getResponse.getStatusCode())
            .as("GET request for created version should return 200 OK")
            .isEqualTo(HttpStatus.OK);
    }

    // ========================================================================
    // Property 5: Duplicate Creation Returns 409 Conflict
    // ========================================================================

    /**
     * Property 5: Duplicate Creation Returns 409 Conflict
     *
     * <p>For any attempt to create a duplicate derivative (same source version and target format),
     * the API SHALL throw DuplicateDerivativeException which is mapped to HTTP 409 Conflict.</p>
     *
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 5: Duplicate creation returns 409 Conflict")
    void duplicateDerivativeCreationThrowsException(
            @ForAll("validCreateDerivativeRequests") CreateDerivativeRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        DocumentVersionId sourceVersionId = DocumentVersionId.generate();
        
        // Simulate duplicate derivative exception
        when(commandHandler.handle(any(CreateDerivativeCommand.class)))
            .thenThrow(new DuplicateDerivativeException(sourceVersionId, request.targetFormat()));

        // When/Then
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> 
            controller.createDerivative(setId, docId, request)))
            .as("Creating duplicate derivative should throw DuplicateDerivativeException for format=%s, version=%d",
                request.targetFormat(), request.sourceVersionNumber())
            .isInstanceOf(DuplicateDerivativeException.class)
            .hasMessageContaining("Derivative already exists");
    }

    // ========================================================================
    // Generators
    // ========================================================================

    @Provide
    Arbitrary<CreateDerivativeRequest> validCreateDerivativeRequests() {
        return Combinators.combine(
            sourceVersionNumbers(),
            targetFormats()
        ).as(CreateDerivativeRequest::new);
    }

    private Arbitrary<Integer> sourceVersionNumbers() {
        return Arbitraries.integers().between(1, 100);
    }

    private Arbitrary<Format> targetFormats() {
        return Arbitraries.of(Format.values());
    }

    // Generators

    @Provide
    Arbitrary<CreateDocumentSetRequest> validCreateDocumentSetRequests() {
        return Combinators.combine(
            documentTypes(),
            uuids(),
            versionStrings(),
            base64Contents(),
            createdByStrings(),
            metadataMaps()
        ).as(CreateDocumentSetRequest::new);
    }

    @Provide
    Arbitrary<AddDocumentRequest> validAddDocumentRequests() {
        return Combinators.combine(
            documentTypes(),
            uuids(),
            versionStrings(),
            base64Contents(),
            createdByStrings(),
            optionalRelatedDocumentIds()
        ).as(AddDocumentRequest::new);
    }

    @Provide
    Arbitrary<AddVersionRequest> validAddVersionRequests() {
        return Combinators.combine(
            base64Contents(),
            createdByStrings()
        ).as(AddVersionRequest::new);
    }

    private Arbitrary<DocumentType> documentTypes() {
        return Arbitraries.of(DocumentType.values());
    }

    private Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    private Arbitrary<String> versionStrings() {
        return Arbitraries.integers().between(0, 99)
            .tuple3()
            .map(tuple -> tuple.get1() + "." + tuple.get2() + "." + tuple.get3());
    }

    private Arbitrary<String> base64Contents() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(100)
            .map(s -> Base64.getEncoder().encodeToString(s.getBytes()));
    }

    private Arbitrary<String> createdByStrings() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50)
            .map(s -> s + "@example.com");
    }

    private Arbitrary<Map<String, String>> metadataMaps() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.just(Map.of()),
            Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
            ).ofMinSize(1).ofMaxSize(5)
        );
    }

    private Arbitrary<UUID> optionalRelatedDocumentIds() {
        return Arbitraries.oneOf(
            Arbitraries.just((UUID) null),
            Arbitraries.create(UUID::randomUUID)
        );
    }

    // Helper methods

    private DocumentSet createDocumentSetFromRequest(CreateDocumentSetRequest request) {
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId(request.schemaId()),
            VersionIdentifier.of(request.schemaVersion())
        );
        
        byte[] contentBytes = Base64.getDecoder().decode(request.content());
        ContentHash contentHash = Content.computeHash(contentBytes);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        Map<String, String> metadata = request.metadata() != null ? request.metadata() : Map.of();
        
        return DocumentSet.createWithDocument(
            request.documentType(),
            schemaRef,
            contentRef,
            contentHash,
            request.createdBy(),
            metadata
        );
    }

    private Document createDocumentFromRequest(AddDocumentRequest request) {
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

    private DocumentSet createDocumentSetWithDocument(Document document) {
        // Create a document set that contains the given document
        // We use reconstitute to create a document set with the specific document
        java.util.Map<com.example.documents.domain.model.DocumentId, Document> documents = 
            new java.util.HashMap<>();
        documents.put(document.id(), document);
        
        return DocumentSet.reconstitute(
            DocumentSetId.generate(),
            documents,
            java.time.Instant.now(),
            "test@example.com",
            Map.of()
        );
    }

    private DocumentSet createDocumentSetWithDocument(UUID setId, Document document) {
        // Create a document set with a specific ID that contains the given document
        java.util.Map<com.example.documents.domain.model.DocumentId, Document> documents = 
            new java.util.HashMap<>();
        documents.put(document.id(), document);
        
        return DocumentSet.reconstitute(
            new DocumentSetId(setId),
            documents,
            java.time.Instant.now(),
            "test@example.com",
            Map.of()
        );
    }

    private Document createDocumentWithVersion(UUID docId, DocumentVersion version) {
        // Create a document with a specific version using reconstitute
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId(UUID.randomUUID()),
            VersionIdentifier.of("1.0.0")
        );
        
        java.util.List<DocumentVersion> versions = new java.util.ArrayList<>();
        versions.add(version);
        
        return Document.reconstitute(
            new com.example.documents.domain.model.DocumentId(docId),
            DocumentType.INVOICE,
            schemaRef,
            versions,
            java.util.List.of(),
            null  // relatedDocumentId
        );
    }

    private DocumentVersion createDocumentVersionFromRequest(AddVersionRequest request, int versionNumber) {
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
}
