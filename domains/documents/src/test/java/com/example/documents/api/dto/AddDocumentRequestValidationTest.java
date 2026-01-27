package com.example.documents.api.dto;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.documents.domain.model.DocumentType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Unit tests for {@link AddDocumentRequest} validation.
 * 
 * <p>Validates: Requirements 9.2 - THE Request_DTO for adding documents SHALL require
 * document type, schema ID, schema version, content (base64), and created by</p>
 */
@DisplayName("AddDocumentRequest Validation")
class AddDocumentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private AddDocumentRequest validRequest() {
        return new AddDocumentRequest(
            DocumentType.ORDER,
            UUID.randomUUID(),
            "2.0.0",
            Base64.getEncoder().encodeToString("document content".getBytes()),
            "admin@example.com",
            null
        );
    }

    @Nested
    @DisplayName("Valid requests")
    class ValidRequests {

        @Test
        @DisplayName("should pass validation with all required fields")
        void validRequestPassesValidation() {
            AddDocumentRequest request = validRequest();

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with null relatedDocumentId (optional field)")
        void validRequestWithNullRelatedDocumentIdPassesValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.CREDIT_NOTE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with relatedDocumentId provided")
        void validRequestWithRelatedDocumentIdPassesValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.DEBIT_NOTE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                UUID.randomUUID()
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Missing required fields")
    class MissingRequiredFields {

        @Test
        @DisplayName("should fail validation when documentType is null")
        void nullDocumentTypeFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                null,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("documentType");
        }

        @Test
        @DisplayName("should fail validation when schemaId is null")
        void nullSchemaIdFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                null,
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("schemaId");
        }

        @Test
        @DisplayName("should fail validation when schemaVersion is null")
        void nullSchemaVersionFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                null,
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("schemaVersion");
        }

        @Test
        @DisplayName("should fail validation when schemaVersion is blank")
        void blankSchemaVersionFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("schemaVersion");
        }

        @Test
        @DisplayName("should fail validation when schemaVersion is whitespace only")
        void whitespaceSchemaVersionFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "   \t  ",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("schemaVersion");
        }

        @Test
        @DisplayName("should fail validation when content is null")
        void nullContentFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                null,
                "user@example.com",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
        }

        @Test
        @DisplayName("should fail validation when content is blank")
        void blankContentFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                "   ",
                "user@example.com",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
        }

        @Test
        @DisplayName("should fail validation when createdBy is null")
        void nullCreatedByFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                null,
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("createdBy");
        }

        @Test
        @DisplayName("should fail validation when createdBy is blank")
        void blankCreatedByFailsValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("content".getBytes()),
                "",
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("createdBy");
        }

        @Test
        @DisplayName("should fail validation with multiple missing required fields")
        void multipleNullFieldsFailValidation() {
            AddDocumentRequest request = new AddDocumentRequest(
                null,
                null,
                null,
                null,
                null,
                null
            );

            Set<ConstraintViolation<AddDocumentRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(5)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                    "documentType",
                    "schemaId",
                    "schemaVersion",
                    "content",
                    "createdBy"
                );
        }
    }
}
