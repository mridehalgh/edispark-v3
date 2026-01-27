package com.example.documents.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.http.ResponseEntity;

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

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for error response structure.
 *
 * <p><b>Property 7: Error Response Structure</b></p>
 * <p>For any ErrorResponse returned by the API, the response SHALL contain a non-null timestamp,
 * a non-empty error code, and a non-empty message.</p>
 *
 * <p><b>Validates: Requirements 8.12</b></p>
 */
class ErrorResponseStructurePropertyTest {

    private final DocumentExceptionHandler handler = new DocumentExceptionHandler();

    /**
     * Property 7: Error Response Structure - Timestamp is non-null
     *
     * <p>For any domain exception handled by the exception handler, the resulting
     * ErrorResponse SHALL have a non-null timestamp.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 7: Error response timestamp is non-null")
    void errorResponseTimestampIsNonNull(@ForAll("domainExceptions") RuntimeException exception) {
        ResponseEntity<ErrorResponse> response = handleException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp())
            .as("ErrorResponse timestamp should be non-null for exception: %s", exception.getClass().getSimpleName())
            .isNotNull();
    }

    /**
     * Property 7: Error Response Structure - Code is non-null and non-empty
     *
     * <p>For any domain exception handled by the exception handler, the resulting
     * ErrorResponse SHALL have a non-null and non-empty error code.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 7: Error response code is non-null and non-empty")
    void errorResponseCodeIsNonNullAndNonEmpty(@ForAll("domainExceptions") RuntimeException exception) {
        ResponseEntity<ErrorResponse> response = handleException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
            .as("ErrorResponse code should be non-null and non-empty for exception: %s", exception.getClass().getSimpleName())
            .isNotNull()
            .isNotBlank();
    }

    /**
     * Property 7: Error Response Structure - Message is non-null and non-empty
     *
     * <p>For any domain exception handled by the exception handler, the resulting
     * ErrorResponse SHALL have a non-null and non-empty message.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 7: Error response message is non-null and non-empty")
    void errorResponseMessageIsNonNullAndNonEmpty(@ForAll("domainExceptions") RuntimeException exception) {
        ResponseEntity<ErrorResponse> response = handleException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
            .as("ErrorResponse message should be non-null and non-empty for exception: %s", exception.getClass().getSimpleName())
            .isNotNull()
            .isNotBlank();
    }

    /**
     * Property 7: Error Response Structure - Details is non-null
     *
     * <p>For any domain exception handled by the exception handler, the resulting
     * ErrorResponse SHALL have a non-null details map (can be empty).</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 7: Error response details is non-null")
    void errorResponseDetailsIsNonNull(@ForAll("domainExceptions") RuntimeException exception) {
        ResponseEntity<ErrorResponse> response = handleException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details())
            .as("ErrorResponse details should be non-null for exception: %s", exception.getClass().getSimpleName())
            .isNotNull();
    }

    /**
     * Property 7: Error Response Structure - All fields valid together
     *
     * <p>For any domain exception handled by the exception handler, the resulting
     * ErrorResponse SHALL have all required fields with valid values.</p>
     */
    @Property(tries = 100)
    @Label("Feature: documents-api, Property 7: Error response has all required fields")
    void errorResponseHasAllRequiredFields(@ForAll("domainExceptions") RuntimeException exception) {
        ResponseEntity<ErrorResponse> response = handleException(exception);

        assertThat(response.getBody())
            .as("ErrorResponse body should not be null")
            .isNotNull()
            .satisfies(errorResponse -> {
                assertThat(errorResponse.timestamp()).isNotNull();
                assertThat(errorResponse.code()).isNotNull().isNotBlank();
                assertThat(errorResponse.message()).isNotNull().isNotBlank();
                assertThat(errorResponse.details()).isNotNull();
            });
    }

    /**
     * Routes the exception to the appropriate handler method.
     */
    private ResponseEntity<ErrorResponse> handleException(RuntimeException exception) {
        return switch (exception) {
            case DocumentSetNotFoundException ex -> handler.handleDocumentSetNotFound(ex);
            case DocumentNotFoundException ex -> handler.handleDocumentNotFound(ex);
            case VersionNotFoundException ex -> handler.handleVersionNotFound(ex);
            case SchemaNotFoundException ex -> handler.handleSchemaNotFound(ex);
            case SchemaVersionNotFoundException ex -> handler.handleSchemaVersionNotFound(ex);
            case DuplicateDerivativeException ex -> handler.handleDuplicateDerivative(ex);
            case SchemaInUseException ex -> handler.handleSchemaInUse(ex);
            case UnsupportedFormatException ex -> handler.handleUnsupportedFormat(ex);
            case InvalidVersionSequenceException ex -> handler.handleInvalidVersionSequence(ex);
            case ContentHashMismatchException ex -> handler.handleContentHashMismatch(ex);
            case IllegalArgumentException ex -> handler.handleIllegalArgument(ex);
            default -> throw new IllegalStateException("Unhandled exception type: " + exception.getClass());
        };
    }

    /**
     * Provides random domain exceptions for property testing.
     */
    @Provide
    Arbitrary<RuntimeException> domainExceptions() {
        return Arbitraries.oneOf(
            documentSetNotFoundExceptions(),
            documentNotFoundExceptions(),
            versionNotFoundExceptions(),
            schemaNotFoundExceptions(),
            schemaVersionNotFoundExceptions(),
            duplicateDerivativeExceptions(),
            schemaInUseExceptions(),
            unsupportedFormatExceptions(),
            invalidVersionSequenceExceptions(),
            contentHashMismatchExceptions(),
            illegalArgumentExceptions()
        );
    }

    private Arbitrary<RuntimeException> documentSetNotFoundExceptions() {
        return Arbitraries.create(() -> new DocumentSetNotFoundException(DocumentSetId.generate()));
    }

    private Arbitrary<RuntimeException> documentNotFoundExceptions() {
        return Arbitraries.create(() -> 
            new DocumentNotFoundException(DocumentSetId.generate(), DocumentId.generate()));
    }

    private Arbitrary<RuntimeException> versionNotFoundExceptions() {
        return Arbitraries.integers().between(1, 1000)
            .map(versionNumber -> new VersionNotFoundException(DocumentId.generate(), versionNumber));
    }

    private Arbitrary<RuntimeException> schemaNotFoundExceptions() {
        return Arbitraries.create(() -> new SchemaNotFoundException(SchemaId.generate()));
    }

    private Arbitrary<RuntimeException> schemaVersionNotFoundExceptions() {
        return versionIdentifiers()
            .map(version -> new SchemaVersionNotFoundException(
                SchemaVersionRef.of(SchemaId.generate(), version)));
    }

    private Arbitrary<RuntimeException> duplicateDerivativeExceptions() {
        return formats()
            .map(format -> new DuplicateDerivativeException(DocumentVersionId.generate(), format));
    }

    private Arbitrary<RuntimeException> schemaInUseExceptions() {
        return versionIdentifiers()
            .map(version -> new SchemaInUseException(SchemaId.generate(), version));
    }

    private Arbitrary<RuntimeException> unsupportedFormatExceptions() {
        return Arbitraries.of(Format.values())
            .tuple2()
            .map(tuple -> new UnsupportedFormatException(tuple.get1(), tuple.get2()));
    }

    private Arbitrary<RuntimeException> invalidVersionSequenceExceptions() {
        return Arbitraries.integers().between(1, 100)
            .tuple2()
            .map(tuple -> new InvalidVersionSequenceException(
                DocumentId.generate(), tuple.get1(), tuple.get2()));
    }

    private Arbitrary<RuntimeException> contentHashMismatchExceptions() {
        return contentHashes().tuple2()
            .map(tuple -> new ContentHashMismatchException(tuple.get1(), tuple.get2()));
    }

    private Arbitrary<RuntimeException> illegalArgumentExceptions() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100)
            .map(IllegalArgumentException::new);
    }

    private Arbitrary<VersionIdentifier> versionIdentifiers() {
        return Arbitraries.integers().between(0, 99)
            .tuple3()
            .map(tuple -> VersionIdentifier.of(
                tuple.get1() + "." + tuple.get2() + "." + tuple.get3()));
    }

    private Arbitrary<Format> formats() {
        return Arbitraries.of(Format.values());
    }

    private Arbitrary<ContentHash> contentHashes() {
        return Arbitraries.strings().withCharRange('a', 'f').ofLength(64)
            .map(ContentHash::sha256);
    }
}
