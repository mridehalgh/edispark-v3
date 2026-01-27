package com.example.documents.api.dto;

import java.util.Base64;
import java.util.Map;
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
 * Unit tests for {@link CreateDocumentSetRequest} validation.
 * 
 * <p>Validates: Requirements 9.1 - THE Request_DTO for creating document sets SHALL require
 * document type, schema ID, schema version, content (base64), and created by</p>
 */
@DisplayName("CreateDocumentSetRequest Validation")
class CreateDocumentSetRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private CreateDocumentSetRequest validRequest() {
        return new CreateDocumentSetRequest(
            DocumentType.INVOICE,
            UUID.randomUUID(),
            "1.0.0",
            Base64.getEncoder().encodeToString("test content".getBytes()),
            "user@example.com",
            Map.of("key", "value")
        );
    }

    @Nested
    @DisplayName("Valid requests")
    class ValidRequests {

        @Test
        @DisplayName("should pass validation with all required fields")
        void validRequestPassesValidation() {
            CreateDocumentSetRequest request = validRequest();

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with null metadata (optional field)")
        void validRequestWithNullMetadataPassesValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("test content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with empty metadata map")
        void validRequestWithEmptyMetadataPassesValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("test content".getBytes()),
                "user@example.com",
                Map.of()
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Missing required fields")
    class MissingRequiredFields {

        @Test
        @DisplayName("should fail validation when documentType is null")
        void nullDocumentTypeFailsValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                null,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("test content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("documentType");
        }

        @Test
        @DisplayName("should fail validation when schemaId is null")
        void nullSchemaIdFailsValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                null,
                "1.0.0",
                Base64.getEncoder().encodeToString("test content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("schemaId");
        }

        @Test
        @DisplayName("should fail validation when schemaVersion is null")
        void nullSchemaVersionFailsValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                null,
                Base64.getEncoder().encodeToString("test content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("schemaVersion");
        }

        @Test
        @DisplayName("should fail validation when schemaVersion is blank")
        void blankSchemaVersionFailsValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "   ",
                Base64.getEncoder().encodeToString("test content".getBytes()),
                "user@example.com",
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("schemaVersion");
        }

        @Test
        @DisplayName("should fail validation when content is null")
        void nullContentFailsValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                null,
                "user@example.com",
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
        }

        @Test
        @DisplayName("should fail validation when content is blank")
        void blankContentFailsValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                "",
                "user@example.com",
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
        }

        @Test
        @DisplayName("should fail validation when createdBy is null")
        void nullCreatedByFailsValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("test content".getBytes()),
                null,
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("createdBy");
        }

        @Test
        @DisplayName("should fail validation when createdBy is blank")
        void blankCreatedByFailsValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                DocumentType.INVOICE,
                UUID.randomUUID(),
                "1.0.0",
                Base64.getEncoder().encodeToString("test content".getBytes()),
                "  ",
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("createdBy");
        }

        @Test
        @DisplayName("should fail validation with multiple missing required fields")
        void multipleNullFieldsFailValidation() {
            CreateDocumentSetRequest request = new CreateDocumentSetRequest(
                null,
                null,
                null,
                null,
                null,
                null
            );

            Set<ConstraintViolation<CreateDocumentSetRequest>> violations = validator.validate(request);

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
