package com.example.documents.api.rest;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class DocumentSetController {

    private final DocumentSetCommandHandler commandHandler;
    private final DocumentSetRepository repository;

    /**
     * Creates a new document set with an initial document.
     *
     * @param request the create document set request
     * @return 201 Created with the document set details
     */
    @PostMapping
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
    public ResponseEntity<DocumentSetResponse> getDocumentSet(@PathVariable UUID id) {
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
    public ResponseEntity<DocumentResponse> addDocument(
            @PathVariable UUID setId,
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
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable UUID setId,
            @PathVariable UUID docId) {
        
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
    public ResponseEntity<DocumentVersionResponse> addVersion(
            @PathVariable UUID setId,
            @PathVariable UUID docId,
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
    public ResponseEntity<DocumentVersionResponse> getVersion(
            @PathVariable UUID setId,
            @PathVariable UUID docId,
            @PathVariable int versionNumber) {
        
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
    public ResponseEntity<DerivativeResponse> createDerivative(
            @PathVariable UUID setId,
            @PathVariable UUID docId,
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
    public ResponseEntity<List<DerivativeResponse>> getDerivatives(
            @PathVariable UUID setId,
            @PathVariable UUID docId) {
        
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
    public ResponseEntity<ValidationResultResponse> validateDocument(
            @PathVariable UUID setId,
            @PathVariable UUID docId,
            @PathVariable int versionNumber) {
        
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
                currentVersion.createdBy()
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
                version.createdBy()
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
