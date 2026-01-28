package com.example.documents.api.rest;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.ResponseEntity;

import com.example.documents.api.dto.AddDocumentRequest;
import com.example.documents.api.dto.AddVersionRequest;
import com.example.documents.api.dto.CreateDerivativeRequest;
import com.example.documents.api.dto.CreateDocumentSetRequest;
import com.example.documents.api.dto.DerivativeResponse;
import com.example.documents.api.dto.DocumentResponse;
import com.example.documents.api.dto.DocumentSetResponse;
import com.example.documents.api.dto.DocumentVersionResponse;
import com.example.documents.api.dto.SchemaRefResponse;
import com.example.documents.api.dto.ValidationResultResponse;
import com.example.documents.application.command.AddDocumentCommand;
import com.example.documents.application.command.AddVersionCommand;
import com.example.documents.application.command.CreateDerivativeCommand;
import com.example.documents.application.command.CreateDocumentSetCommand;
import com.example.documents.application.command.ValidateDocumentCommand;
import com.example.documents.application.handler.DocumentSetCommandHandler;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.Derivative;
import com.example.documents.domain.model.Document;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentType;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.model.DocumentVersionId;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.TransformationMethod;
import com.example.documents.domain.model.ValidationResult;
import com.example.documents.domain.model.ValidationError;
import com.example.documents.domain.model.ValidationWarning;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.SchemaVersion;
import com.example.documents.domain.model.SchemaVersionId;
import com.example.documents.domain.repository.DocumentSetRepository;
import com.example.documents.domain.repository.SchemaRepository;
import com.example.documents.api.dto.CreateSchemaRequest;
import com.example.documents.api.dto.AddSchemaVersionRequest;
import com.example.documents.api.dto.SchemaResponse;
import com.example.documents.api.dto.SchemaVersionResponse;
import com.example.documents.application.command.CreateSchemaCommand;
import com.example.documents.application.command.AddSchemaVersionCommand;
import com.example.documents.application.handler.SchemaCommandHandler;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for response structure completeness across all API endpoints.
 *
 * <p><b>Property 3: Response Structure Completeness</b></p>
 * <p>For any successful API response, the response body SHALL contain all required fields
 * as specified in the Response DTO definitions (no null values for required fields, correct types).</p>
 *
 * <p><b>Validates: Requirements 1.5, 2.6, 3.5, 4.5, 5.5, 6.5, 7.5</b></p>
 */
class ResponseStructureCompletenessPropertyTest {

    // ========================================================================
    // Property 3: DocumentSetResponse Structure Completeness
    // ========================================================================

    /**
     * Property 3: DocumentSetResponse has all required fields
     *
     * <p>For any valid CreateDocumentSetRequest, the response SHALL contain all required fields:
     * id, createdAt, createdBy, metadata, and documents list.</p>
     *
     * <p><b>Validates: Requirements 1.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: DocumentSetResponse has all required fields")
    void documentSetResponseHasAllRequiredFields(
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
        
        DocumentSetResponse body = response.getBody();
        assertThat(body.id())
            .as("DocumentSetResponse.id should not be null")
            .isNotNull();
        assertThat(body.createdAt())
            .as("DocumentSetResponse.createdAt should not be null")
            .isNotNull();
        assertThat(body.createdBy())
            .as("DocumentSetResponse.createdBy should not be null or blank")
            .isNotNull()
            .isNotBlank();
        assertThat(body.metadata())
            .as("DocumentSetResponse.metadata should not be null")
            .isNotNull();
        assertThat(body.documents())
            .as("DocumentSetResponse.documents should not be null")
            .isNotNull();
    }

    /**
     * Property 3: DocumentSetResponse.DocumentSummary has all required fields
     *
     * <p>For any valid CreateDocumentSetRequest, each document summary in the response
     * SHALL contain all required fields: id, type, and versionCount.</p>
     *
     * <p><b>Validates: Requirements 1.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: DocumentSummary has all required fields")
    void documentSummaryHasAllRequiredFields(
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
        assertThat(response.getBody().documents()).isNotEmpty();
        
        response.getBody().documents().forEach(summary -> {
            assertThat(summary.id())
                .as("DocumentSummary.id should not be null")
                .isNotNull();
            assertThat(summary.type())
                .as("DocumentSummary.type should not be null")
                .isNotNull();
            assertThat(summary.versionCount())
                .as("DocumentSummary.versionCount should be positive")
                .isGreaterThan(0);
        });
    }

    // ========================================================================
    // Property 3: DocumentResponse Structure Completeness
    // ========================================================================

    /**
     * Property 3: DocumentResponse has all required fields
     *
     * <p>For any valid AddDocumentRequest, the response SHALL contain all required fields:
     * id, type, schemaRef, versionCount, currentVersion, and derivatives list.</p>
     *
     * <p><b>Validates: Requirements 2.6</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: DocumentResponse has all required fields")
    void documentResponseHasAllRequiredFields(
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
        
        DocumentResponse body = response.getBody();
        assertThat(body.id())
            .as("DocumentResponse.id should not be null")
            .isNotNull();
        assertThat(body.type())
            .as("DocumentResponse.type should not be null")
            .isNotNull();
        assertThat(body.schemaRef())
            .as("DocumentResponse.schemaRef should not be null")
            .isNotNull();
        assertThat(body.versionCount())
            .as("DocumentResponse.versionCount should be positive")
            .isGreaterThan(0);
        assertThat(body.currentVersion())
            .as("DocumentResponse.currentVersion should not be null")
            .isNotNull();
        assertThat(body.derivatives())
            .as("DocumentResponse.derivatives should not be null")
            .isNotNull();
    }

    /**
     * Property 3: SchemaRefResponse has all required fields
     *
     * <p>For any valid AddDocumentRequest, the schemaRef in the response
     * SHALL contain all required fields: schemaId and version.</p>
     *
     * <p><b>Validates: Requirements 2.6</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: SchemaRefResponse has all required fields")
    void schemaRefResponseHasAllRequiredFields(
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
        SchemaRefResponse schemaRef = response.getBody().schemaRef();
        
        assertThat(schemaRef.schemaId())
            .as("SchemaRefResponse.schemaId should not be null")
            .isNotNull();
        assertThat(schemaRef.version())
            .as("SchemaRefResponse.version should not be null or blank")
            .isNotNull()
            .isNotBlank();
    }

    // ========================================================================
    // Property 3: DocumentVersionResponse Structure Completeness
    // ========================================================================

    /**
     * Property 3: DocumentVersionResponse has all required fields
     *
     * <p>For any valid AddVersionRequest, the response SHALL contain all required fields:
     * id, versionNumber, contentHash, createdAt, and createdBy.</p>
     *
     * <p><b>Validates: Requirements 3.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: DocumentVersionResponse has all required fields")
    void documentVersionResponseHasAllRequiredFields(
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
        
        DocumentVersionResponse body = response.getBody();
        assertThat(body.id())
            .as("DocumentVersionResponse.id should not be null")
            .isNotNull();
        assertThat(body.versionNumber())
            .as("DocumentVersionResponse.versionNumber should be positive")
            .isGreaterThan(0);
        assertThat(body.contentHash())
            .as("DocumentVersionResponse.contentHash should not be null or blank")
            .isNotNull()
            .isNotBlank();
        assertThat(body.createdAt())
            .as("DocumentVersionResponse.createdAt should not be null")
            .isNotNull();
        assertThat(body.createdBy())
            .as("DocumentVersionResponse.createdBy should not be null or blank")
            .isNotNull()
            .isNotBlank();
    }

    // ========================================================================
    // Property 3: DerivativeResponse Structure Completeness
    // ========================================================================

    /**
     * Property 3: DerivativeResponse has all required fields
     *
     * <p>For any valid CreateDerivativeRequest, the response SHALL contain all required fields:
     * id, sourceVersionId, targetFormat, contentHash, transformationMethod, and createdAt.</p>
     *
     * <p><b>Validates: Requirements 4.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: DerivativeResponse has all required fields")
    void derivativeResponseHasAllRequiredFields(
            @ForAll("validCreateDerivativeRequests") CreateDerivativeRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        Derivative derivative = createDerivativeFromRequest(request);
        when(commandHandler.handle(any(CreateDerivativeCommand.class))).thenReturn(derivative);

        // When
        ResponseEntity<DerivativeResponse> response = controller.createDerivative(setId, docId, request);

        // Then
        assertThat(response.getBody())
            .as("Response body should not be null")
            .isNotNull();
        
        DerivativeResponse body = response.getBody();
        assertThat(body.id())
            .as("DerivativeResponse.id should not be null")
            .isNotNull();
        assertThat(body.sourceVersionId())
            .as("DerivativeResponse.sourceVersionId should not be null")
            .isNotNull();
        assertThat(body.targetFormat())
            .as("DerivativeResponse.targetFormat should not be null")
            .isNotNull();
        assertThat(body.contentHash())
            .as("DerivativeResponse.contentHash should not be null or blank")
            .isNotNull()
            .isNotBlank();
        assertThat(body.transformationMethod())
            .as("DerivativeResponse.transformationMethod should not be null or blank")
            .isNotNull()
            .isNotBlank();
        assertThat(body.createdAt())
            .as("DerivativeResponse.createdAt should not be null")
            .isNotNull();
    }

    /**
     * Property 3: DerivativeResponse list completeness
     *
     * <p>For any document with derivatives, the GET derivatives endpoint SHALL return
     * a list where each derivative has all required fields.</p>
     *
     * <p><b>Validates: Requirements 4.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: Derivative list has complete responses")
    void derivativeListHasCompleteResponses(
            @ForAll("validCreateDerivativeRequests") CreateDerivativeRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        
        // Create a document with a derivative
        Derivative derivative = createDerivativeFromRequest(request);
        Document document = createDocumentWithDerivative(docId, derivative);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When
        ResponseEntity<List<DerivativeResponse>> response = controller.getDerivatives(setId, docId);

        // Then
        assertThat(response.getBody())
            .as("Response body should not be null")
            .isNotNull()
            .isNotEmpty();
        
        response.getBody().forEach(derivativeResponse -> {
            assertThat(derivativeResponse.id())
                .as("DerivativeResponse.id should not be null")
                .isNotNull();
            assertThat(derivativeResponse.sourceVersionId())
                .as("DerivativeResponse.sourceVersionId should not be null")
                .isNotNull();
            assertThat(derivativeResponse.targetFormat())
                .as("DerivativeResponse.targetFormat should not be null")
                .isNotNull();
            assertThat(derivativeResponse.contentHash())
                .as("DerivativeResponse.contentHash should not be null or blank")
                .isNotNull()
                .isNotBlank();
            assertThat(derivativeResponse.transformationMethod())
                .as("DerivativeResponse.transformationMethod should not be null or blank")
                .isNotNull()
                .isNotBlank();
            assertThat(derivativeResponse.createdAt())
                .as("DerivativeResponse.createdAt should not be null")
                .isNotNull();
        });
    }

    // ========================================================================
    // Property 3: ValidationResultResponse Structure Completeness
    // ========================================================================

    /**
     * Property 3: ValidationResultResponse has all required fields for valid documents
     *
     * <p>For any validation result, the response SHALL contain all required fields:
     * valid flag, errors list, and warnings list.</p>
     *
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: ValidationResultResponse has all required fields")
    void validationResultResponseHasAllRequiredFields(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        int versionNumber = 1;
        
        // Create a document set with a document and version
        DocumentVersion version = createDocumentVersionFromRequest(request, versionNumber);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        // Mock validation result
        ValidationResult validationResult = ValidationResult.success();
        when(commandHandler.handle(any(ValidateDocumentCommand.class))).thenReturn(validationResult);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When
        ResponseEntity<ValidationResultResponse> response = controller.validateDocument(setId, docId, versionNumber);

        // Then
        assertThat(response.getBody())
            .as("Response body should not be null")
            .isNotNull();
        
        ValidationResultResponse body = response.getBody();
        // Note: valid is a boolean primitive, so it's always non-null
        assertThat(body.errors())
            .as("ValidationResultResponse.errors should not be null")
            .isNotNull();
        assertThat(body.warnings())
            .as("ValidationResultResponse.warnings should not be null")
            .isNotNull();
    }

    /**
     * Property 3: ValidationErrorResponse has all required fields
     *
     * <p>When validation errors are present, each error SHALL contain
     * all required fields: path and message.</p>
     *
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: ValidationErrorResponse has all required fields")
    void validationErrorResponseHasAllRequiredFields(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        int versionNumber = 1;
        
        DocumentVersion version = createDocumentVersionFromRequest(request, versionNumber);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        // Mock validation result with errors
        ValidationResult validationResult = ValidationResult.failure(
            List.of(ValidationError.of("/field1", "Error message 1", "ERR001"))
        );
        when(commandHandler.handle(any(ValidateDocumentCommand.class))).thenReturn(validationResult);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When
        ResponseEntity<ValidationResultResponse> response = controller.validateDocument(setId, docId, versionNumber);

        // Then
        assertThat(response.getBody()).isNotNull();
        
        // If there are errors, verify their structure
        response.getBody().errors().forEach(error -> {
            assertThat(error.path())
                .as("ValidationErrorResponse.path should not be null or blank")
                .isNotNull()
                .isNotBlank();
            assertThat(error.message())
                .as("ValidationErrorResponse.message should not be null or blank")
                .isNotNull()
                .isNotBlank();
        });
    }

    /**
     * Property 3: ValidationWarningResponse has all required fields
     *
     * <p>When validation warnings are present, each warning SHALL contain
     * all required fields: path and message.</p>
     *
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: ValidationWarningResponse has all required fields")
    void validationWarningResponseHasAllRequiredFields(
            @ForAll("validAddVersionRequests") AddVersionRequest request) {
        
        // Given
        DocumentSetCommandHandler commandHandler = mock(DocumentSetCommandHandler.class);
        DocumentSetRepository repository = mock(DocumentSetRepository.class);
        DocumentSetController controller = new DocumentSetController(commandHandler, repository, mock(com.example.documents.application.query.DocumentSetQueryHandler.class));
        
        UUID setId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        int versionNumber = 1;
        
        DocumentVersion version = createDocumentVersionFromRequest(request, versionNumber);
        Document document = createDocumentWithVersion(docId, version);
        DocumentSet documentSet = createDocumentSetWithDocument(setId, document);
        
        // Mock validation result with warnings
        ValidationResult validationResult = ValidationResult.successWithWarnings(
            List.of(ValidationWarning.of("/field1", "Warning message 1"))
        );
        when(commandHandler.handle(any(ValidateDocumentCommand.class))).thenReturn(validationResult);
        when(repository.findById(any(DocumentSetId.class))).thenReturn(Optional.of(documentSet));

        // When
        ResponseEntity<ValidationResultResponse> response = controller.validateDocument(setId, docId, versionNumber);

        // Then
        assertThat(response.getBody()).isNotNull();
        
        // If there are warnings, verify their structure
        response.getBody().warnings().forEach(warning -> {
            assertThat(warning.path())
                .as("ValidationWarningResponse.path should not be null or blank")
                .isNotNull()
                .isNotBlank();
            assertThat(warning.message())
                .as("ValidationWarningResponse.message should not be null or blank")
                .isNotNull()
                .isNotBlank();
        });
    }

    // ========================================================================
    // Property 3: SchemaResponse Structure Completeness
    // ========================================================================

    /**
     * Property 3: SchemaResponse has all required fields
     *
     * <p>For any valid CreateSchemaRequest, the response SHALL contain all required fields:
     * id, name, format, and versions list.</p>
     *
     * <p><b>Validates: Requirements 6.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: SchemaResponse has all required fields")
    void schemaResponseHasAllRequiredFields(
            @ForAll("validCreateSchemaRequests") CreateSchemaRequest request) {
        
        // Given
        SchemaCommandHandler commandHandler = mock(SchemaCommandHandler.class);
        SchemaRepository repository = mock(SchemaRepository.class);
        SchemaController controller = new SchemaController(commandHandler, repository);
        
        Schema schema = createSchemaFromRequest(request);
        when(commandHandler.handle(any(CreateSchemaCommand.class))).thenReturn(schema);

        // When
        ResponseEntity<SchemaResponse> response = controller.createSchema(request);

        // Then
        assertThat(response.getBody())
            .as("Response body should not be null")
            .isNotNull();
        
        SchemaResponse body = response.getBody();
        assertThat(body.id())
            .as("SchemaResponse.id should not be null")
            .isNotNull();
        assertThat(body.name())
            .as("SchemaResponse.name should not be null or blank")
            .isNotNull()
            .isNotBlank();
        assertThat(body.format())
            .as("SchemaResponse.format should not be null")
            .isNotNull();
        assertThat(body.versions())
            .as("SchemaResponse.versions should not be null")
            .isNotNull();
    }

    /**
     * Property 3: SchemaVersionSummary has all required fields
     *
     * <p>For any schema with versions, each version summary SHALL contain
     * all required fields: id, versionIdentifier, createdAt, and deprecated flag.</p>
     *
     * <p><b>Validates: Requirements 6.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: SchemaVersionSummary has all required fields")
    void schemaVersionSummaryHasAllRequiredFields(
            @ForAll("validAddSchemaVersionRequests") AddSchemaVersionRequest request) {
        
        // Given
        SchemaCommandHandler commandHandler = mock(SchemaCommandHandler.class);
        SchemaRepository repository = mock(SchemaRepository.class);
        SchemaController controller = new SchemaController(commandHandler, repository);
        
        UUID schemaId = UUID.randomUUID();
        SchemaVersion schemaVersion = createSchemaVersionFromRequest(request);
        Schema schema = createSchemaWithVersion(schemaId, schemaVersion);
        
        when(repository.findById(any(SchemaId.class))).thenReturn(Optional.of(schema));

        // When
        ResponseEntity<SchemaResponse> response = controller.getSchema(schemaId);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().versions()).isNotEmpty();
        
        response.getBody().versions().forEach(summary -> {
            assertThat(summary.id())
                .as("SchemaVersionSummary.id should not be null")
                .isNotNull();
            assertThat(summary.versionIdentifier())
                .as("SchemaVersionSummary.versionIdentifier should not be null or blank")
                .isNotNull()
                .isNotBlank();
            assertThat(summary.createdAt())
                .as("SchemaVersionSummary.createdAt should not be null")
                .isNotNull();
            // deprecated is a boolean primitive, always non-null
        });
    }

    // ========================================================================
    // Property 3: SchemaVersionResponse Structure Completeness
    // ========================================================================

    /**
     * Property 3: SchemaVersionResponse has all required fields
     *
     * <p>For any valid AddSchemaVersionRequest, the response SHALL contain all required fields:
     * id, versionIdentifier, createdAt, and deprecated flag.</p>
     *
     * <p><b>Validates: Requirements 7.5</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 3: SchemaVersionResponse has all required fields")
    void schemaVersionResponseHasAllRequiredFields(
            @ForAll("validAddSchemaVersionRequests") AddSchemaVersionRequest request) {
        
        // Given
        SchemaCommandHandler commandHandler = mock(SchemaCommandHandler.class);
        SchemaRepository repository = mock(SchemaRepository.class);
        SchemaController controller = new SchemaController(commandHandler, repository);
        
        UUID schemaId = UUID.randomUUID();
        SchemaVersion schemaVersion = createSchemaVersionFromRequest(request);
        when(commandHandler.handle(any(AddSchemaVersionCommand.class))).thenReturn(schemaVersion);

        // When
        ResponseEntity<SchemaVersionResponse> response = controller.addVersion(schemaId, request);

        // Then
        assertThat(response.getBody())
            .as("Response body should not be null")
            .isNotNull();
        
        SchemaVersionResponse body = response.getBody();
        assertThat(body.id())
            .as("SchemaVersionResponse.id should not be null")
            .isNotNull();
        assertThat(body.versionIdentifier())
            .as("SchemaVersionResponse.versionIdentifier should not be null or blank")
            .isNotNull()
            .isNotBlank();
        assertThat(body.createdAt())
            .as("SchemaVersionResponse.createdAt should not be null")
            .isNotNull();
        // deprecated is a boolean primitive, always non-null
    }

    // ========================================================================
    // Generators
    // ========================================================================

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

    @Provide
    Arbitrary<CreateDerivativeRequest> validCreateDerivativeRequests() {
        return Combinators.combine(
            sourceVersionNumbers(),
            targetFormats()
        ).as(CreateDerivativeRequest::new);
    }

    @Provide
    Arbitrary<CreateSchemaRequest> validCreateSchemaRequests() {
        return Combinators.combine(
            schemaNames(),
            schemaFormats()
        ).as(CreateSchemaRequest::new);
    }

    @Provide
    Arbitrary<AddSchemaVersionRequest> validAddSchemaVersionRequests() {
        return Combinators.combine(
            versionStrings(),
            base64Contents()
        ).as(AddSchemaVersionRequest::new);
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

    private Arbitrary<Integer> sourceVersionNumbers() {
        return Arbitraries.integers().between(1, 100);
    }

    private Arbitrary<Format> targetFormats() {
        return Arbitraries.of(Format.values());
    }

    private Arbitrary<String> schemaNames() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50)
            .map(s -> "Schema-" + s);
    }

    private Arbitrary<SchemaFormat> schemaFormats() {
        return Arbitraries.of(SchemaFormat.values());
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

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

    private DocumentVersion createDocumentVersionFromRequest(AddVersionRequest request, int versionNumber) {
        byte[] contentBytes = Base64.getDecoder().decode(request.content());
        ContentHash contentHash = Content.computeHash(contentBytes);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        if (versionNumber == 1) {
            return DocumentVersion.createFirst(contentRef, contentHash, request.createdBy());
        }
        
        DocumentVersion previousVersion = DocumentVersion.createFirst(
            contentRef, 
            contentHash, 
            request.createdBy()
        );
        
        DocumentVersion currentVersion = previousVersion;
        for (int i = 2; i <= versionNumber; i++) {
            currentVersion = DocumentVersion.createNext(currentVersion, contentRef, contentHash, request.createdBy());
        }
        
        return currentVersion;
    }

    private Derivative createDerivativeFromRequest(CreateDerivativeRequest request) {
        DocumentVersionId sourceVersionId = DocumentVersionId.generate();
        byte[] contentBytes = "derivative-content".getBytes();
        ContentHash contentHash = Content.computeHash(contentBytes);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        return Derivative.create(
            sourceVersionId,
            request.targetFormat(),
            contentRef,
            contentHash,
            TransformationMethod.XSLT
        );
    }

    private DocumentSet createDocumentSetWithDocument(UUID setId, Document document) {
        Map<DocumentId, Document> documents = new java.util.HashMap<>();
        documents.put(document.id(), document);
        
        return DocumentSet.reconstitute(
            new DocumentSetId(setId),
            documents,
            Instant.now(),
            "test@example.com",
            Map.of()
        );
    }

    private Document createDocumentWithVersion(UUID docId, DocumentVersion version) {
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId(UUID.randomUUID()),
            VersionIdentifier.of("1.0.0")
        );
        
        List<DocumentVersion> versions = new java.util.ArrayList<>();
        versions.add(version);
        
        return Document.reconstitute(
            new DocumentId(docId),
            DocumentType.INVOICE,
            schemaRef,
            versions,
            List.of(),
            null
        );
    }

    private Document createDocumentWithDerivative(UUID docId, Derivative derivative) {
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
            new SchemaId(UUID.randomUUID()),
            VersionIdentifier.of("1.0.0")
        );
        
        byte[] contentBytes = "test-content".getBytes();
        ContentHash contentHash = Content.computeHash(contentBytes);
        ContentRef contentRef = ContentRef.of(contentHash);
        
        DocumentVersion version = DocumentVersion.createFirst(contentRef, contentHash, "test@example.com");
        
        return Document.reconstitute(
            new DocumentId(docId),
            DocumentType.INVOICE,
            schemaRef,
            List.of(version),
            List.of(derivative),
            null
        );
    }

    private Schema createSchemaFromRequest(CreateSchemaRequest request) {
        return Schema.create(request.name(), request.format());
    }

    private SchemaVersion createSchemaVersionFromRequest(AddSchemaVersionRequest request) {
        byte[] definitionBytes = Base64.getDecoder().decode(request.definition());
        ContentHash contentHash = Content.computeHash(definitionBytes);
        ContentRef definitionRef = ContentRef.of(contentHash);
        VersionIdentifier versionIdentifier = VersionIdentifier.of(request.versionIdentifier());
        
        return SchemaVersion.create(versionIdentifier, definitionRef);
    }

    private Schema createSchemaWithVersion(UUID schemaId, SchemaVersion schemaVersion) {
        List<SchemaVersion> versions = new java.util.ArrayList<>();
        versions.add(schemaVersion);
        
        return Schema.reconstitute(
            new SchemaId(schemaId),
            "Test Schema",
            SchemaFormat.XSD,
            versions
        );
    }
}
