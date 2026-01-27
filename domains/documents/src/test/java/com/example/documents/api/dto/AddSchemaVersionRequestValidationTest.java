package com.example.documents.api.dto;

import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Unit tests for {@link AddSchemaVersionRequest} validation.
 * 
 * <p>Validates: Requirements 9.6 - THE Request_DTO for adding schema versions SHALL require
 * version identifier and definition (base64)</p>
 */
@DisplayName("AddSchemaVersionRequest Validation")
class AddSchemaVersionRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private AddSchemaVersionRequest validRequest() {
        return new AddSchemaVersionRequest(
            "1.0.0",
            Base64.getEncoder().encodeToString("<schema>test</schema>".getBytes())
        );
    }

    @Nested
    @DisplayName("Valid requests")
    class ValidRequests {

        @Test
        @DisplayName("should pass validation with all required fields")
        void validRequestPassesValidation() {
            AddSchemaVersionRequest request = validRequest();

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with semantic version identifier")
        void validRequestWithSemanticVersionPassesValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "2.1.0",
                Base64.getEncoder().encodeToString("<schema>content</schema>".getBytes())
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with date-based version identifier")
        void validRequestWithDateVersionPassesValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "2024-01-15",
                Base64.getEncoder().encodeToString("<schema>content</schema>".getBytes())
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with simple version identifier")
        void validRequestWithSimpleVersionPassesValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "v1",
                Base64.getEncoder().encodeToString("<schema>content</schema>".getBytes())
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Missing required fields")
    class MissingRequiredFields {

        @Test
        @DisplayName("should fail validation when versionIdentifier is null")
        void nullVersionIdentifierFailsValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                null,
                Base64.getEncoder().encodeToString("<schema>content</schema>".getBytes())
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("versionIdentifier");
        }

        @Test
        @DisplayName("should fail validation when versionIdentifier is blank")
        void blankVersionIdentifierFailsValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "   ",
                Base64.getEncoder().encodeToString("<schema>content</schema>".getBytes())
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("versionIdentifier");
        }

        @Test
        @DisplayName("should fail validation when versionIdentifier is empty")
        void emptyVersionIdentifierFailsValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "",
                Base64.getEncoder().encodeToString("<schema>content</schema>".getBytes())
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("versionIdentifier");
        }

        @Test
        @DisplayName("should fail validation when definition is null")
        void nullDefinitionFailsValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "1.0.0",
                null
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("definition");
        }

        @Test
        @DisplayName("should fail validation when definition is blank")
        void blankDefinitionFailsValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "1.0.0",
                "   "
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("definition");
        }

        @Test
        @DisplayName("should fail validation when definition is empty")
        void emptyDefinitionFailsValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                "1.0.0",
                ""
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("definition");
        }

        @Test
        @DisplayName("should fail validation with multiple missing required fields")
        void multipleNullFieldsFailValidation() {
            AddSchemaVersionRequest request = new AddSchemaVersionRequest(
                null,
                null
            );

            Set<ConstraintViolation<AddSchemaVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(2)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                    "versionIdentifier",
                    "definition"
                );
        }
    }
}
