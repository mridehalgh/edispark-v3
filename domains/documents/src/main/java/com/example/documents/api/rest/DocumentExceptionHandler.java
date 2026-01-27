package com.example.documents.api.rest;

import com.example.documents.api.dto.ErrorResponse;
import com.example.documents.application.handler.DocumentNotFoundException;
import com.example.documents.application.handler.DocumentSetNotFoundException;
import com.example.documents.application.handler.SchemaNotFoundException;
import com.example.documents.application.handler.SchemaVersionNotFoundException;
import com.example.documents.application.handler.UnsupportedFormatException;
import com.example.documents.application.handler.VersionNotFoundException;
import com.example.documents.domain.model.ContentHashMismatchException;
import com.example.documents.domain.model.DuplicateDerivativeException;
import com.example.documents.domain.model.InvalidVersionSequenceException;
import com.example.documents.domain.model.SchemaInUseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Exception handler for the Documents API.
 *
 * <p>Maps domain exceptions to appropriate HTTP responses with consistent error structure.
 * This handler is scoped to the DocumentSetController and SchemaController classes.</p>
 */
@ControllerAdvice
@Slf4j
public class DocumentExceptionHandler {

    /**
     * Handles DocumentSetNotFoundException.
     *
     * @param ex the exception
     * @return 404 Not Found with error details
     */
    @ExceptionHandler(DocumentSetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentSetNotFound(DocumentSetNotFoundException ex) {
        log.debug("Document set not found: {}", ex.documentSetId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(
                "DOCUMENT_SET_NOT_FOUND",
                ex.getMessage(),
                Map.of("documentSetId", ex.documentSetId().toString())
            ));
    }

    /**
     * Handles DocumentNotFoundException.
     *
     * @param ex the exception
     * @return 404 Not Found with error details
     */
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException ex) {
        log.debug("Document not found: {} in document set: {}", ex.documentId(), ex.documentSetId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(
                "DOCUMENT_NOT_FOUND",
                ex.getMessage(),
                Map.of(
                    "documentSetId", ex.documentSetId().toString(),
                    "documentId", ex.documentId().toString()
                )
            ));
    }

    /**
     * Handles VersionNotFoundException.
     *
     * @param ex the exception
     * @return 404 Not Found with error details
     */
    @ExceptionHandler(VersionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVersionNotFound(VersionNotFoundException ex) {
        log.debug("Version {} not found for document: {}", ex.versionNumber(), ex.documentId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(
                "VERSION_NOT_FOUND",
                ex.getMessage(),
                Map.of(
                    "documentId", ex.documentId().toString(),
                    "versionNumber", ex.versionNumber()
                )
            ));
    }

    /**
     * Handles SchemaNotFoundException.
     *
     * @param ex the exception
     * @return 404 Not Found with error details
     */
    @ExceptionHandler(SchemaNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSchemaNotFound(SchemaNotFoundException ex) {
        log.debug("Schema not found: {}", ex.schemaId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(
                "SCHEMA_NOT_FOUND",
                ex.getMessage(),
                Map.of("schemaId", ex.schemaId().toString())
            ));
    }

    /**
     * Handles SchemaVersionNotFoundException.
     *
     * @param ex the exception
     * @return 404 Not Found with error details
     */
    @ExceptionHandler(SchemaVersionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSchemaVersionNotFound(SchemaVersionNotFoundException ex) {
        log.debug("Schema version not found: {}", ex.schemaVersionRef());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(
                "SCHEMA_VERSION_NOT_FOUND",
                ex.getMessage(),
                Map.of(
                    "schemaId", ex.schemaVersionRef().schemaId().toString(),
                    "version", ex.schemaVersionRef().version().value()
                )
            ));
    }

    /**
     * Handles DuplicateDerivativeException.
     *
     * @param ex the exception
     * @return 409 Conflict with error details
     */
    @ExceptionHandler(DuplicateDerivativeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateDerivative(DuplicateDerivativeException ex) {
        log.debug("Duplicate derivative: source version {} with format {}", 
            ex.sourceVersionId(), ex.targetFormat());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(
                "DUPLICATE_DERIVATIVE",
                ex.getMessage(),
                Map.of(
                    "sourceVersionId", ex.sourceVersionId().toString(),
                    "targetFormat", ex.targetFormat().name()
                )
            ));
    }

    /**
     * Handles SchemaInUseException.
     *
     * @param ex the exception
     * @return 409 Conflict with error details
     */
    @ExceptionHandler(SchemaInUseException.class)
    public ResponseEntity<ErrorResponse> handleSchemaInUse(SchemaInUseException ex) {
        log.debug("Schema in use: {} version {}", ex.schemaId(), ex.versionIdentifier());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(
                "SCHEMA_IN_USE",
                ex.getMessage(),
                Map.of(
                    "schemaId", ex.schemaId().toString(),
                    "versionIdentifier", ex.versionIdentifier().value()
                )
            ));
    }

    /**
     * Handles UnsupportedFormatException.
     *
     * @param ex the exception
     * @return 400 Bad Request with error details
     */
    @ExceptionHandler(UnsupportedFormatException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFormat(UnsupportedFormatException ex) {
        log.debug("Unsupported format transformation: {} to {}", 
            ex.sourceFormat(), ex.targetFormat());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "UNSUPPORTED_FORMAT",
                ex.getMessage(),
                Map.of(
                    "sourceFormat", ex.sourceFormat().name(),
                    "targetFormat", ex.targetFormat().name()
                )
            ));
    }

    /**
     * Handles InvalidVersionSequenceException.
     *
     * @param ex the exception
     * @return 400 Bad Request with error details
     */
    @ExceptionHandler(InvalidVersionSequenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVersionSequence(InvalidVersionSequenceException ex) {
        log.debug("Invalid version sequence for document {}: expected {} but got {}", 
            ex.documentId(), ex.expectedVersionNumber(), ex.actualVersionNumber());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "INVALID_VERSION_SEQUENCE",
                ex.getMessage(),
                Map.of(
                    "documentId", ex.documentId().toString(),
                    "expectedVersionNumber", ex.expectedVersionNumber(),
                    "actualVersionNumber", ex.actualVersionNumber()
                )
            ));
    }

    /**
     * Handles ContentHashMismatchException.
     *
     * @param ex the exception
     * @return 400 Bad Request with error details
     */
    @ExceptionHandler(ContentHashMismatchException.class)
    public ResponseEntity<ErrorResponse> handleContentHashMismatch(ContentHashMismatchException ex) {
        log.debug("Content hash mismatch: expected {} but got {}", 
            ex.expectedHash(), ex.actualHash());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "CONTENT_HASH_MISMATCH",
                ex.getMessage(),
                Map.of(
                    "expectedHash", ex.expectedHash().toFullString(),
                    "actualHash", ex.actualHash().toFullString()
                )
            ));
    }

    /**
     * Handles validation errors from @Valid annotations.
     *
     * @param ex the exception containing validation errors
     * @return 400 Bad Request with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.debug("Validation failed with {} errors", ex.getBindingResult().getErrorCount());
        
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"
            ))
            .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "VALIDATION_ERROR",
                "Request validation failed",
                Map.of("fieldErrors", fieldErrors)
            ));
    }

    /**
     * Handles IllegalArgumentException.
     *
     * @param ex the exception
     * @return 400 Bad Request with error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "BAD_REQUEST",
                ex.getMessage()
            ));
    }
}
