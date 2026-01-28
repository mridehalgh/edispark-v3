package com.example.documents.api.rest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.example.documents.api.dto.ErrorResponse;
import com.example.documents.application.handler.DocumentNotFoundException;
import com.example.documents.application.handler.DocumentSetNotFoundException;
import com.example.documents.application.handler.SchemaNotFoundException;
import com.example.documents.application.handler.SchemaVersionNotFoundException;
import com.example.documents.application.handler.UnsupportedFormatException;
import com.example.documents.application.handler.VersionNotFoundException;
import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentHashMismatchException;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentVersionId;
import com.example.documents.domain.model.DuplicateDerivativeException;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.InvalidVersionSequenceException;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaInUseException;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.model.VersionIdentifier;

/**
 * Unit tests for {@link DocumentExceptionHandler}.
 *
 * <p>Property 6: Exception to HTTP Status Mapping - For any domain exception thrown during
 * request processing, the Exception Handler SHALL map it to the correct HTTP status code
 * as defined in the status code mapping table.</p>
 *
 * <p>Validates: Requirements 8.1-8.11</p>
 */
@DisplayName("DocumentExceptionHandler")
class DocumentExceptionHandlerTest {

    private DocumentExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DocumentExceptionHandler();
    }

    @Nested
    @DisplayName("404 Not Found exceptions")
    class NotFoundExceptions {

        @Test
        @DisplayName("DocumentSetNotFoundException returns 404 with correct error code")
        void documentSetNotFoundReturns404() {
            DocumentSetId documentSetId = DocumentSetId.generate();
            DocumentSetNotFoundException exception = new DocumentSetNotFoundException(documentSetId);

            ResponseEntity<ErrorResponse> response = handler.handleDocumentSetNotFound(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("DOCUMENT_SET_NOT_FOUND");
        }

        @Test
        @DisplayName("DocumentSetNotFoundException includes documentSetId in details")
        void documentSetNotFoundIncludesDetails() {
            DocumentSetId documentSetId = DocumentSetId.generate();
            DocumentSetNotFoundException exception = new DocumentSetNotFoundException(documentSetId);

            ResponseEntity<ErrorResponse> response = handler.handleDocumentSetNotFound(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("documentSetId", documentSetId.toString());
        }

        @Test
        @DisplayName("DocumentNotFoundException returns 404 with correct error code")
        void documentNotFoundReturns404() {
            DocumentSetId documentSetId = DocumentSetId.generate();
            DocumentId documentId = DocumentId.generate();
            DocumentNotFoundException exception = new DocumentNotFoundException(documentSetId, documentId);

            ResponseEntity<ErrorResponse> response = handler.handleDocumentNotFound(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("DOCUMENT_NOT_FOUND");
        }

        @Test
        @DisplayName("DocumentNotFoundException includes documentSetId and documentId in details")
        void documentNotFoundIncludesDetails() {
            DocumentSetId documentSetId = DocumentSetId.generate();
            DocumentId documentId = DocumentId.generate();
            DocumentNotFoundException exception = new DocumentNotFoundException(documentSetId, documentId);

            ResponseEntity<ErrorResponse> response = handler.handleDocumentNotFound(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("documentSetId", documentSetId.toString())
                .containsEntry("documentId", documentId.toString());
        }

        @Test
        @DisplayName("VersionNotFoundException returns 404 with correct error code")
        void versionNotFoundReturns404() {
            DocumentId documentId = DocumentId.generate();
            int versionNumber = 5;
            VersionNotFoundException exception = new VersionNotFoundException(documentId, versionNumber);

            ResponseEntity<ErrorResponse> response = handler.handleVersionNotFound(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("VERSION_NOT_FOUND");
        }

        @Test
        @DisplayName("VersionNotFoundException includes documentId and versionNumber in details")
        void versionNotFoundIncludesDetails() {
            DocumentId documentId = DocumentId.generate();
            int versionNumber = 5;
            VersionNotFoundException exception = new VersionNotFoundException(documentId, versionNumber);

            ResponseEntity<ErrorResponse> response = handler.handleVersionNotFound(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("documentId", documentId.toString())
                .containsEntry("versionNumber", versionNumber);
        }

        @Test
        @DisplayName("SchemaNotFoundException returns 404 with correct error code")
        void schemaNotFoundReturns404() {
            SchemaId schemaId = SchemaId.generate();
            SchemaNotFoundException exception = new SchemaNotFoundException(schemaId);

            ResponseEntity<ErrorResponse> response = handler.handleSchemaNotFound(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("SCHEMA_NOT_FOUND");
        }

        @Test
        @DisplayName("SchemaNotFoundException includes schemaId in details")
        void schemaNotFoundIncludesDetails() {
            SchemaId schemaId = SchemaId.generate();
            SchemaNotFoundException exception = new SchemaNotFoundException(schemaId);

            ResponseEntity<ErrorResponse> response = handler.handleSchemaNotFound(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("schemaId", schemaId.toString());
        }

        @Test
        @DisplayName("SchemaVersionNotFoundException returns 404 with correct error code")
        void schemaVersionNotFoundReturns404() {
            SchemaVersionRef schemaVersionRef = SchemaVersionRef.of(
                SchemaId.generate(),
                VersionIdentifier.of("1.0.0")
            );
            SchemaVersionNotFoundException exception = new SchemaVersionNotFoundException(schemaVersionRef);

            ResponseEntity<ErrorResponse> response = handler.handleSchemaVersionNotFound(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("SCHEMA_VERSION_NOT_FOUND");
        }

        @Test
        @DisplayName("SchemaVersionNotFoundException includes schemaId and version in details")
        void schemaVersionNotFoundIncludesDetails() {
            SchemaId schemaId = SchemaId.generate();
            VersionIdentifier version = VersionIdentifier.of("2.1.0");
            SchemaVersionRef schemaVersionRef = SchemaVersionRef.of(schemaId, version);
            SchemaVersionNotFoundException exception = new SchemaVersionNotFoundException(schemaVersionRef);

            ResponseEntity<ErrorResponse> response = handler.handleSchemaVersionNotFound(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("schemaId", schemaId.toString())
                .containsEntry("version", version.value());
        }
    }

    @Nested
    @DisplayName("409 Conflict exceptions")
    class ConflictExceptions {

        @Test
        @DisplayName("DuplicateDerivativeException returns 409 with correct error code")
        void duplicateDerivativeReturns409() {
            DocumentVersionId sourceVersionId = DocumentVersionId.generate();
            Format targetFormat = Format.PDF;
            DuplicateDerivativeException exception = new DuplicateDerivativeException(sourceVersionId, targetFormat);

            ResponseEntity<ErrorResponse> response = handler.handleDuplicateDerivative(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("DUPLICATE_DERIVATIVE");
        }

        @Test
        @DisplayName("DuplicateDerivativeException includes sourceVersionId and targetFormat in details")
        void duplicateDerivativeIncludesDetails() {
            DocumentVersionId sourceVersionId = DocumentVersionId.generate();
            Format targetFormat = Format.PDF;
            DuplicateDerivativeException exception = new DuplicateDerivativeException(sourceVersionId, targetFormat);

            ResponseEntity<ErrorResponse> response = handler.handleDuplicateDerivative(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("sourceVersionId", sourceVersionId.toString())
                .containsEntry("targetFormat", targetFormat.name());
        }

        @Test
        @DisplayName("SchemaInUseException returns 409 with correct error code")
        void schemaInUseReturns409() {
            SchemaId schemaId = SchemaId.generate();
            VersionIdentifier versionIdentifier = VersionIdentifier.of("1.0.0");
            SchemaInUseException exception = new SchemaInUseException(schemaId, versionIdentifier);

            ResponseEntity<ErrorResponse> response = handler.handleSchemaInUse(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("SCHEMA_IN_USE");
        }

        @Test
        @DisplayName("SchemaInUseException includes schemaId and versionIdentifier in details")
        void schemaInUseIncludesDetails() {
            SchemaId schemaId = SchemaId.generate();
            VersionIdentifier versionIdentifier = VersionIdentifier.of("2.0.0");
            SchemaInUseException exception = new SchemaInUseException(schemaId, versionIdentifier);

            ResponseEntity<ErrorResponse> response = handler.handleSchemaInUse(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("schemaId", schemaId.toString())
                .containsEntry("versionIdentifier", versionIdentifier.value());
        }
    }

    @Nested
    @DisplayName("400 Bad Request exceptions")
    class BadRequestExceptions {

        @Test
        @DisplayName("InvalidPaginationTokenException returns 400 with correct error code")
        void invalidPaginationTokenReturns400() {
            com.example.documents.application.handler.InvalidPaginationTokenException exception = 
                new com.example.documents.application.handler.InvalidPaginationTokenException(
                    "Invalid or corrupted pagination token", 
                    new IllegalArgumentException("Bad token"));

            ResponseEntity<ErrorResponse> response = handler.handleInvalidPaginationToken(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("INVALID_PAGINATION_TOKEN");
        }

        @Test
        @DisplayName("InvalidPaginationTokenException has correct message")
        void invalidPaginationTokenHasCorrectMessage() {
            com.example.documents.application.handler.InvalidPaginationTokenException exception = 
                new com.example.documents.application.handler.InvalidPaginationTokenException(
                    "Token decode failed", 
                    new RuntimeException());

            ResponseEntity<ErrorResponse> response = handler.handleInvalidPaginationToken(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message())
                .isEqualTo("The provided pagination token is invalid or expired");
        }

        @Test
        @DisplayName("IllegalArgumentException for pagination returns 400 with INVALID_PARAMETER code")
        void illegalArgumentForPaginationReturns400() {
            IllegalArgumentException exception = new IllegalArgumentException("Limit must be between 1 and 100");

            ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
        }

        @Test
        @DisplayName("UnsupportedFormatException returns 400 with correct error code")
        void unsupportedFormatReturns400() {
            Format sourceFormat = Format.XML;
            Format targetFormat = Format.EDI;
            UnsupportedFormatException exception = new UnsupportedFormatException(sourceFormat, targetFormat);

            ResponseEntity<ErrorResponse> response = handler.handleUnsupportedFormat(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("UNSUPPORTED_FORMAT");
        }

        @Test
        @DisplayName("UnsupportedFormatException includes sourceFormat and targetFormat in details")
        void unsupportedFormatIncludesDetails() {
            Format sourceFormat = Format.JSON;
            Format targetFormat = Format.PDF;
            UnsupportedFormatException exception = new UnsupportedFormatException(sourceFormat, targetFormat);

            ResponseEntity<ErrorResponse> response = handler.handleUnsupportedFormat(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("sourceFormat", sourceFormat.name())
                .containsEntry("targetFormat", targetFormat.name());
        }

        @Test
        @DisplayName("InvalidVersionSequenceException returns 400 with correct error code")
        void invalidVersionSequenceReturns400() {
            DocumentId documentId = DocumentId.generate();
            int expectedVersion = 2;
            int actualVersion = 5;
            InvalidVersionSequenceException exception = 
                new InvalidVersionSequenceException(documentId, expectedVersion, actualVersion);

            ResponseEntity<ErrorResponse> response = handler.handleInvalidVersionSequence(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("INVALID_VERSION_SEQUENCE");
        }

        @Test
        @DisplayName("InvalidVersionSequenceException includes documentId and version numbers in details")
        void invalidVersionSequenceIncludesDetails() {
            DocumentId documentId = DocumentId.generate();
            int expectedVersion = 3;
            int actualVersion = 7;
            InvalidVersionSequenceException exception = 
                new InvalidVersionSequenceException(documentId, expectedVersion, actualVersion);

            ResponseEntity<ErrorResponse> response = handler.handleInvalidVersionSequence(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("documentId", documentId.toString())
                .containsEntry("expectedVersionNumber", expectedVersion)
                .containsEntry("actualVersionNumber", actualVersion);
        }

        @Test
        @DisplayName("ContentHashMismatchException returns 400 with correct error code")
        void contentHashMismatchReturns400() {
            ContentHash expectedHash = ContentHash.sha256("abc123");
            ContentHash actualHash = ContentHash.sha256("def456");
            ContentHashMismatchException exception = new ContentHashMismatchException(expectedHash, actualHash);

            ResponseEntity<ErrorResponse> response = handler.handleContentHashMismatch(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("CONTENT_HASH_MISMATCH");
        }

        @Test
        @DisplayName("ContentHashMismatchException includes expectedHash and actualHash in details")
        void contentHashMismatchIncludesDetails() {
            ContentHash expectedHash = ContentHash.sha256("expected123");
            ContentHash actualHash = ContentHash.sha256("actual456");
            ContentHashMismatchException exception = new ContentHashMismatchException(expectedHash, actualHash);

            ResponseEntity<ErrorResponse> response = handler.handleContentHashMismatch(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details())
                .containsEntry("expectedHash", expectedHash.toFullString())
                .containsEntry("actualHash", actualHash.toFullString());
        }

        @Test
        @DisplayName("IllegalArgumentException returns 400 with correct error code")
        void illegalArgumentReturns400() {
            IllegalArgumentException exception = new IllegalArgumentException("Invalid input provided");

            ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
        }

        @Test
        @DisplayName("IllegalArgumentException includes exception message")
        void illegalArgumentIncludesMessage() {
            String errorMessage = "The provided value is not valid";
            IllegalArgumentException exception = new IllegalArgumentException(errorMessage);

            ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).isEqualTo(errorMessage);
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("MethodArgumentNotValidException returns 400 with VALIDATION_ERROR code")
        void validationErrorReturns400() {
            MethodArgumentNotValidException exception = createValidationException(
                List.of(new FieldError("request", "documentType", "must not be null"))
            );

            ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("MethodArgumentNotValidException includes field errors in details")
        void validationErrorIncludesFieldErrors() {
            List<FieldError> fieldErrors = List.of(
                new FieldError("request", "documentType", "must not be null"),
                new FieldError("request", "content", "must not be blank")
            );
            MethodArgumentNotValidException exception = createValidationException(fieldErrors);

            ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details()).containsKey("fieldErrors");
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> errors = 
                (List<Map<String, String>>) response.getBody().details().get("fieldErrors");
            
            assertThat(errors).hasSize(2);
            assertThat(errors).extracting(m -> m.get("field"))
                .containsExactlyInAnyOrder("documentType", "content");
        }

        @Test
        @DisplayName("MethodArgumentNotValidException has correct message")
        void validationErrorHasCorrectMessage() {
            MethodArgumentNotValidException exception = createValidationException(
                List.of(new FieldError("request", "schemaId", "must not be null"))
            );

            ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).isEqualTo("Request validation failed");
        }

        @Test
        @DisplayName("MethodArgumentNotValidException handles null default message")
        void validationErrorHandlesNullMessage() {
            FieldError fieldError = new FieldError("request", "field", null, false, null, null, null);
            MethodArgumentNotValidException exception = createValidationException(List.of(fieldError));

            ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(exception);

            assertThat(response.getBody()).isNotNull();
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> errors = 
                (List<Map<String, String>>) response.getBody().details().get("fieldErrors");
            
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).get("message")).isEqualTo("Invalid value");
        }

        private MethodArgumentNotValidException createValidationException(List<FieldError> fieldErrors) {
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);
            when(bindingResult.getErrorCount()).thenReturn(fieldErrors.size());
            
            return new MethodArgumentNotValidException(null, bindingResult);
        }
    }

    @Nested
    @DisplayName("Error response structure")
    class ErrorResponseStructure {

        @Test
        @DisplayName("All error responses have non-null timestamp")
        void errorResponsesHaveTimestamp() {
            DocumentSetNotFoundException exception = 
                new DocumentSetNotFoundException(DocumentSetId.generate());

            ResponseEntity<ErrorResponse> response = handler.handleDocumentSetNotFound(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().timestamp()).isNotNull();
        }

        @Test
        @DisplayName("All error responses have non-null code")
        void errorResponsesHaveCode() {
            SchemaNotFoundException exception = new SchemaNotFoundException(SchemaId.generate());

            ResponseEntity<ErrorResponse> response = handler.handleSchemaNotFound(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isNotNull();
            assertThat(response.getBody().code()).isNotBlank();
        }

        @Test
        @DisplayName("All error responses have non-null message")
        void errorResponsesHaveMessage() {
            DuplicateDerivativeException exception = 
                new DuplicateDerivativeException(DocumentVersionId.generate(), Format.JSON);

            ResponseEntity<ErrorResponse> response = handler.handleDuplicateDerivative(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).isNotNull();
            assertThat(response.getBody().message()).isNotBlank();
        }

        @Test
        @DisplayName("All error responses have non-null details map")
        void errorResponsesHaveDetails() {
            IllegalArgumentException exception = new IllegalArgumentException("test error");

            ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(exception);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().details()).isNotNull();
        }
    }
}
