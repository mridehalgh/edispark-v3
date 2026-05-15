package com.example.documents.api.rest;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.example.common.api.PaginatedResponse;
import com.example.common.pagination.PaginatedResult;
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
import com.example.documents.application.handler.DocumentNotFoundException;
import com.example.documents.application.handler.DocumentSetNotFoundException;
import com.example.documents.application.handler.VersionNotFoundException;
import com.example.documents.application.query.DocumentContentQueryService;
import com.example.documents.application.query.DocumentSetQueryHandler;
import com.example.documents.application.query.RetrievedContent;
import com.example.documents.application.query.ListDocumentSetsQuery;
import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Derivative;
import com.example.documents.domain.model.Document;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.ValidationResult;
import com.example.documents.domain.model.VersionIdentifier;
import com.example.documents.domain.repository.DocumentSetRepository;

import jakarta.validation.Valid;

/**
 * REST controller for document set operations.
 *
 * <p>Handles HTTP requests for creating and retrieving document sets,
 * as well as operations on documents, versions, and derivatives within sets.</p>
 *
 * <p>Requirements: 1.1, 1.2, 1.3, 1.4, 1.5</p>
 */
@RestController
@RequestMapping("/api/document-sets")
@Tag(name = "Document Sets", description = "Manage document sets, documents, versions, and derivatives")
public class DocumentSetController {

    private final DocumentSetCommandHandler commandHandler;
    private final DocumentSetRepository repository;
    private final DocumentSetQueryHandler queryHandler;
    private final DocumentContentQueryService documentContentQueryService;

    private static final int DEFAULT_LIMIT = 20;

    @Autowired
    public DocumentSetController(
            DocumentSetCommandHandler commandHandler,
            DocumentSetRepository repository,
            DocumentSetQueryHandler queryHandler,
            DocumentContentQueryService documentContentQueryService) {
        this.commandHandler = commandHandler;
        this.repository = repository;
        this.queryHandler = queryHandler;
        this.documentContentQueryService = documentContentQueryService;
    }

    DocumentSetController(
            DocumentSetCommandHandler commandHandler,
            DocumentSetRepository repository,
            DocumentSetQueryHandler queryHandler) {
        this(commandHandler, repository, queryHandler, null);
    }

    /**
     * Lists document sets with pagination support.
     * 
     * @param limit optional page size (1-100, default 20)
     * @param nextToken optional continuation token from previous page
     * @return paginated list of document sets
     */
    @GetMapping
    @Operation(summary = "List document sets", 
               description = "Retrieves document sets with pagination. Use nextToken or nextUrl from response to fetch subsequent pages.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document sets retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid limit or pagination token")
    })
    public ResponseEntity<PaginatedResponse<DocumentSetResponse>> listDocumentSets(
            @Parameter(description = "Page size (1-100, default 20)") 
            @org.springframework.web.bind.annotation.RequestParam(name = "limit", required = false) Integer limit,
            @Parameter(description = "Continuation token from previous page") 
            @org.springframework.web.bind.annotation.RequestParam(name = "nextToken", required = false) String nextToken) {
        
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        ListDocumentSetsQuery query = ListDocumentSetsQuery.of(effectiveLimit, nextToken);
        PaginatedResult<DocumentSet> result = queryHandler.handle(query);
        
        PaginatedResult<DocumentSetResponse> mappedResult = result.map(this::mapToResponse);
        String baseUrl = currentRequestUri();
        PaginatedResponse<DocumentSetResponse> response = PaginatedResponse.from(mappedResult, baseUrl, effectiveLimit, nextToken);
        
        return ResponseEntity.ok(response);
    }

    private String currentRequestUri() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return "/api/document-sets";
        }

        return ServletUriComponentsBuilder.fromCurrentRequestUri().toUriString();
    }

    /**
     * Creates a new document set with an initial document.
     *
     * @param request the create document set request
     * @return 201 Created with the document set details
     */
    @PostMapping
    @Operation(summary = "Create a new document set", 
               description = "Creates a new document set with an initial document. The document content must be Base64 encoded.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Document set created successfully",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentSetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Schema not found")
    })
    public ResponseEntity<DocumentSetResponse> createDocumentSet(
            @Valid @RequestBody CreateDocumentSetRequest request) {
        
        // Decode Base64 content
        byte[] contentBytes = Base64.getDecoder().decode(request.content());
        
        // Create schema version reference
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
                new SchemaId(request.schemaId()),
                VersionIdentifier.of(request.schemaVersion())
        );
        
        // Create content with default format (will be determined by schema)
        // Using XML as default format; actual format determination happens in command handler
        Content content = Content.of(contentBytes, Format.XML);
        
        // Build command
        CreateDocumentSetCommand command = new CreateDocumentSetCommand(
                request.documentType(),
                schemaRef,
                content,
                request.createdBy(),
                request.metadata()
        );
        
        // Execute command
        DocumentSet documentSet = commandHandler.handle(command);
        
        // Map to response
        DocumentSetResponse response = mapToResponse(documentSet);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a document set by its unique identifier.
     *
     * @param id the document set identifier
     * @return 200 OK with the document set details if found
     * @throws DocumentSetNotFoundException if the document set does not exist
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a document set", description = "Retrieves a document set by its unique identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document set found",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentSetResponse.class))),
        @ApiResponse(responseCode = "404", description = "Document set not found")
    })
    public ResponseEntity<DocumentSetResponse> getDocumentSet(
            @Parameter(description = "Document set UUID") @PathVariable UUID id) {
        DocumentSetId documentSetId = new DocumentSetId(id);
        
        DocumentSet documentSet = repository.findById(documentSetId)
                .orElseThrow(() -> new DocumentSetNotFoundException(documentSetId));
        
        DocumentSetResponse response = mapToResponse(documentSet);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Adds a document to an existing document set.
     *
     * @param setId the document set identifier
     * @param request the add document request
     * @return 201 Created with the document details
     * @throws DocumentSetNotFoundException if the document set does not exist
     *
     * <p>Requirements: 2.1, 2.2, 2.3</p>
     */
    @PostMapping("/{setId}/documents")
    @Operation(summary = "Add a document to a set", 
               description = "Adds a new document to an existing document set. Content must be Base64 encoded.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Document added successfully",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Document set or schema not found")
    })
    public ResponseEntity<DocumentResponse> addDocument(
            @Parameter(description = "Document set UUID") @PathVariable UUID setId,
            @Valid @RequestBody AddDocumentRequest request) {
        
        // Decode Base64 content
        byte[] contentBytes = Base64.getDecoder().decode(request.content());
        
        // Create schema version reference
        SchemaVersionRef schemaRef = SchemaVersionRef.of(
                new SchemaId(request.schemaId()),
                VersionIdentifier.of(request.schemaVersion())
        );
        
        // Create content with default format (will be determined by schema)
        Content content = Content.of(contentBytes, Format.XML);
        
        // Create related document ID if provided
        DocumentId relatedDocumentId = request.relatedDocumentId() != null
                ? new DocumentId(request.relatedDocumentId())
                : null;
        
        // Build command
        AddDocumentCommand command = new AddDocumentCommand(
                new DocumentSetId(setId),
                request.documentType(),
                schemaRef,
                content,
                request.createdBy(),
                relatedDocumentId
        );
        
        // Execute command
        Document document = commandHandler.handle(command);
        
        // Map to response
        DocumentResponse response = mapToDocumentResponse(document);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a document by its unique identifier within a document set.
     *
     * @param setId the document set identifier
     * @param docId the document identifier
     * @return 200 OK with the document details if found
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist within the set
     *
     * <p>Requirements: 2.4, 2.5</p>
     */
    @GetMapping("/{setId}/documents/{docId}")
    @Operation(summary = "Get a document", description = "Retrieves a document by its unique identifier within a document set")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document found",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Document set or document not found")
    })
    public ResponseEntity<DocumentResponse> getDocument(
            @Parameter(description = "Document set UUID") @PathVariable UUID setId,
            @Parameter(description = "Document UUID") @PathVariable UUID docId) {
        
        DocumentSetId documentSetId = new DocumentSetId(setId);
        DocumentId documentId = new DocumentId(docId);
        
        // Find the document set
        DocumentSet documentSet = repository.findById(documentSetId)
                .orElseThrow(() -> new DocumentSetNotFoundException(documentSetId));
        
        // Find the document within the set
        Document document = documentSet.getDocument(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentSetId, documentId));
        
        // Map to response
        DocumentResponse response = mapToDocumentResponse(document);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Adds a new version to an existing document.
     *
     * @param setId the document set identifier
     * @param docId the document identifier
     * @param request the add version request containing content and creator
     * @return 201 Created with the version details
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist within the set
     *
     * <p>Requirements: 3.1, 3.2</p>
     */
    @PostMapping("/{setId}/documents/{docId}/versions")
    @Operation(summary = "Add a document version", 
               description = "Adds a new version to an existing document. Content must be Base64 encoded.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Version added successfully",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentVersionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Document set or document not found")
    })
    public ResponseEntity<DocumentVersionResponse> addVersion(
            @Parameter(description = "Document set UUID") @PathVariable UUID setId,
            @Parameter(description = "Document UUID") @PathVariable UUID docId,
            @Valid @RequestBody AddVersionRequest request) {
        
        // Decode Base64 content
        byte[] contentBytes = Base64.getDecoder().decode(request.content());
        
        // Create content with default format (will be determined by document's schema)
        Content content = Content.of(contentBytes, Format.XML);
        
        // Build command
        AddVersionCommand command = new AddVersionCommand(
                new DocumentSetId(setId),
                new DocumentId(docId),
                content,
                request.createdBy()
        );
        
        // Execute command
        DocumentVersion version = commandHandler.handle(command);
        
        // Map to response
        DocumentVersionResponse response = mapToVersionResponse(version);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a specific version of a document by version number.
     *
     * @param setId the document set identifier
     * @param docId the document identifier
     * @param versionNumber the version number to retrieve
     * @return 200 OK with the version details if found
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist within the set
     * @throws VersionNotFoundException if the version does not exist within the document
     *
     * <p>Requirements: 3.3, 3.4</p>
     */
    @GetMapping("/{setId}/documents/{docId}/versions/{versionNumber}")
    @Operation(summary = "Get a document version", description = "Retrieves a specific version of a document by version number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Version found",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DocumentVersionResponse.class))),
        @ApiResponse(responseCode = "404", description = "Document set, document, or version not found")
    })
    public ResponseEntity<DocumentVersionResponse> getVersion(
            @Parameter(description = "Document set UUID") @PathVariable UUID setId,
            @Parameter(description = "Document UUID") @PathVariable UUID docId,
            @Parameter(description = "Version number (1-based)") @PathVariable int versionNumber) {
        
        DocumentSetId documentSetId = new DocumentSetId(setId);
        DocumentId documentId = new DocumentId(docId);
        
        // Find the document set
        DocumentSet documentSet = repository.findById(documentSetId)
                .orElseThrow(() -> new DocumentSetNotFoundException(documentSetId));
        
        // Find the document within the set
        Document document = documentSet.getDocument(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentSetId, documentId));
        
        // Find the version within the document
        DocumentVersion version = document.getVersion(versionNumber)
                .orElseThrow(() -> new VersionNotFoundException(documentId, versionNumber));
        
        // Map to response
        DocumentVersionResponse response = mapToVersionResponse(version);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{setId}/documents/{docId}/versions/{versionNumber}/content")
    @Operation(summary = "Get document version content",
               description = "Retrieves the raw content bytes for a specific document version")
    @ApiResponses(value = {
        @ApiResponse(
                responseCode = "200",
                description = "Version content found",
                content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/octet-stream",
                        schema = @Schema(type = "string", format = "binary")),
                headers = {
                    @Header(name = "X-Document-Set-Id", description = "Document set identifier"),
                    @Header(name = "X-Document-Id", description = "Document identifier"),
                    @Header(name = "X-Document-Version", description = "Document version number"),
                    @Header(name = "X-Content-Hash", description = "Stored content hash"),
                    @Header(name = "X-Document-Format", description = "Stored document format")
                }),
        @ApiResponse(responseCode = "404", description = "Document set, document, version, or content not found")
    })
    public ResponseEntity<byte[]> getVersionContent(
            @Parameter(description = "Document set UUID") @PathVariable UUID setId,
            @Parameter(description = "Document UUID") @PathVariable UUID docId,
            @Parameter(description = "Version number (1-based)") @PathVariable int versionNumber) {

        RetrievedContent content = documentContentQueryService.getVersionContent(
                new DocumentSetId(setId),
                new DocumentId(docId),
                versionNumber);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Document-Set-Id", setId.toString())
                .header("X-Document-Id", docId.toString())
                .header("X-Document-Version", Integer.toString(versionNumber))
                .header("X-Content-Hash", content.contentHash())
                .header("X-Document-Format", content.format().name())
                .body(content.bytes());
    }

    /**
     * Creates a derivative from a document version.
     *
     * @param setId the document set identifier
     * @param docId the document identifier
     * @param request the create derivative request containing source version and target format
     * @return 201 Created with the derivative details
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist within the set
     * @throws VersionNotFoundException if the source version does not exist
     *
     * <p>Requirements: 4.1, 4.2, 4.3</p>
     */
    @PostMapping("/{setId}/documents/{docId}/derivatives")
    @Operation(summary = "Create a derivative", 
               description = "Creates a derivative (transformed version) from a document version in a different format")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Derivative created successfully",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = DerivativeResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data or duplicate derivative"),
        @ApiResponse(responseCode = "404", description = "Document set, document, or version not found")
    })
    public ResponseEntity<DerivativeResponse> createDerivative(
            @Parameter(description = "Document set UUID") @PathVariable UUID setId,
            @Parameter(description = "Document UUID") @PathVariable UUID docId,
            @Valid @RequestBody CreateDerivativeRequest request) {
        
        // Build command
        CreateDerivativeCommand command = new CreateDerivativeCommand(
                new DocumentSetId(setId),
                new DocumentId(docId),
                request.sourceVersionNumber(),
                request.targetFormat()
        );
        
        // Execute command
        Derivative derivative = commandHandler.handle(command);
        
        // Map to response
        DerivativeResponse response = mapToDerivativeResponse(derivative);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all derivatives for a document.
     *
     * @param setId the document set identifier
     * @param docId the document identifier
     * @return 200 OK with list of derivative details
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist within the set
     *
     * <p>Requirements: 4.4</p>
     */
    @GetMapping("/{setId}/documents/{docId}/derivatives")
    @Operation(summary = "Get all derivatives", description = "Retrieves all derivatives for a document")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Derivatives retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Document set or document not found")
    })
    public ResponseEntity<List<DerivativeResponse>> getDerivatives(
            @Parameter(description = "Document set UUID") @PathVariable UUID setId,
            @Parameter(description = "Document UUID") @PathVariable UUID docId) {
        
        DocumentSetId documentSetId = new DocumentSetId(setId);
        DocumentId documentId = new DocumentId(docId);
        
        // Find the document set
        DocumentSet documentSet = repository.findById(documentSetId)
                .orElseThrow(() -> new DocumentSetNotFoundException(documentSetId));
        
        // Find the document within the set
        Document document = documentSet.getDocument(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentSetId, documentId));
        
        // Map derivatives to response
        var derivativeResponses = document.derivatives().stream()
                .map(this::mapToDerivativeResponse)
                .toList();
        
        return ResponseEntity.ok(derivativeResponses);
    }

    /**
     * Validates a document version against its schema.
     *
     * @param setId the document set identifier
     * @param docId the document identifier
     * @param versionNumber the version number to validate
     * @return 200 OK with validation result if valid, 422 Unprocessable Entity if invalid
     * @throws DocumentSetNotFoundException if the document set does not exist
     * @throws DocumentNotFoundException if the document does not exist within the set
     * @throws VersionNotFoundException if the version does not exist
     *
     * <p>Requirements: 5.1, 5.2, 5.3, 5.4</p>
     */
    @PostMapping("/{setId}/documents/{docId}/versions/{versionNumber}/validate")
    @Operation(summary = "Validate a document version", 
               description = "Validates a document version against its schema. Returns 200 if valid, 422 if invalid.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document is valid",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ValidationResultResponse.class))),
        @ApiResponse(responseCode = "422", description = "Document is invalid",
                     content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ValidationResultResponse.class))),
        @ApiResponse(responseCode = "404", description = "Document set, document, or version not found")
    })
    public ResponseEntity<ValidationResultResponse> validateDocument(
            @Parameter(description = "Document set UUID") @PathVariable UUID setId,
            @Parameter(description = "Document UUID") @PathVariable UUID docId,
            @Parameter(description = "Version number (1-based)") @PathVariable int versionNumber) {
        
        // Build command
        ValidateDocumentCommand command = ValidateDocumentCommand.of(
                new DocumentSetId(setId),
                new DocumentId(docId),
                versionNumber
        );
        
        // Execute validation
        ValidationResult result = commandHandler.handle(command);
        
        // Map to response
        ValidationResultResponse response = mapToValidationResponse(result);
        
        // Return 200 OK if valid, 422 Unprocessable Entity if invalid
        if (result.valid()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
    }

    /**
     * Maps a DocumentSet domain object to a DocumentSetResponse DTO.
     *
     * @param documentSet the domain object
     * @return the response DTO
     */
    private DocumentSetResponse mapToResponse(DocumentSet documentSet) {
        var documentSummaries = documentSet.getAllDocuments().stream()
                .map(doc -> new DocumentSetResponse.DocumentSummary(
                        doc.id().value(),
                        doc.type(),
                        doc.versionCount()
                ))
                .toList();

        return new DocumentSetResponse(
                documentSet.id().value(),
                documentSet.createdAt(),
                documentSet.createdBy(),
                documentSet.metadata(),
                documentSummaries
        );
    }

    /**
     * Maps a Document domain object to a DocumentResponse DTO.
     *
     * @param document the domain object
     * @return the response DTO
     */
    private DocumentResponse mapToDocumentResponse(Document document) {
        // Map schema reference
        SchemaRefResponse schemaRefResponse = new SchemaRefResponse(
                document.schemaRef().schemaId().value(),
                document.schemaRef().version().value()
        );
        
        // Map current version
        var currentVersion = document.getCurrentVersion();
        DocumentVersionResponse currentVersionResponse = new DocumentVersionResponse(
                currentVersion.id().value(),
                currentVersion.versionNumber(),
                currentVersion.contentHash().toFullString(),
                currentVersion.createdAt(),
                currentVersion.createdBy(),
                currentVersion.format(),
                currentVersion.parseStatus(),
                currentVersion.messageType(),
                currentVersion.parseErrors()
        );
        
        // Map derivatives
        var derivativeResponses = document.derivatives().stream()
                .map(derivative -> new DerivativeResponse(
                        derivative.id().value(),
                        derivative.sourceVersionId().value(),
                        derivative.targetFormat(),
                        derivative.contentHash().toFullString(),
                        derivative.method().name(),
                        derivative.createdAt()
                ))
                .toList();
        
        return new DocumentResponse(
                document.id().value(),
                document.type(),
                schemaRefResponse,
                document.versionCount(),
                currentVersionResponse,
                derivativeResponses
        );
    }

    /**
     * Maps a DocumentVersion domain object to a DocumentVersionResponse DTO.
     *
     * @param version the domain object
     * @return the response DTO
     */
    private DocumentVersionResponse mapToVersionResponse(DocumentVersion version) {
        return new DocumentVersionResponse(
                version.id().value(),
                version.versionNumber(),
                version.contentHash().toFullString(),
                version.createdAt(),
                version.createdBy(),
                version.format(),
                version.parseStatus(),
                version.messageType(),
                version.parseErrors()
        );
    }

    /**
     * Maps a Derivative domain object to a DerivativeResponse DTO.
     *
     * @param derivative the domain object
     * @return the response DTO
     */
    private DerivativeResponse mapToDerivativeResponse(Derivative derivative) {
        return new DerivativeResponse(
                derivative.id().value(),
                derivative.sourceVersionId().value(),
                derivative.targetFormat(),
                derivative.contentHash().toFullString(),
                derivative.method().name(),
                derivative.createdAt()
        );
    }

    /**
     * Maps a ValidationResult domain object to a ValidationResultResponse DTO.
     *
     * @param result the domain validation result
     * @return the response DTO
     */
    private ValidationResultResponse mapToValidationResponse(ValidationResult result) {
        var errorResponses = result.errors().stream()
                .map(error -> new ValidationResultResponse.ValidationErrorResponse(
                        error.path(),
                        error.message()
                ))
                .toList();

        var warningResponses = result.warnings().stream()
                .map(warning -> new ValidationResultResponse.ValidationWarningResponse(
                        warning.path(),
                        warning.message()
                ))
                .toList();

        return new ValidationResultResponse(result.valid(), errorResponses, warningResponses);
    }
}
