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
 * Unit tests for {@link AddVersionRequest} validation.
 * 
 * <p>Validates: Requirements 9.3 - THE Request_DTO for adding versions SHALL require
 * content (base64) and created by</p>
 */
@DisplayName("AddVersionRequest Validation")
class AddVersionRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private AddVersionRequest validRequest() {
        return new AddVersionRequest(
            Base64.getEncoder().encodeToString("version content".getBytes()),
            "editor@example.com"
        );
    }

    @Nested
    @DisplayName("Valid requests")
    class ValidRequests {

        @Test
        @DisplayName("should pass validation with all required fields")
        void validRequestPassesValidation() {
            AddVersionRequest request = validRequest();

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with minimal valid content")
        void validRequestWithMinimalContentPassesValidation() {
            AddVersionRequest request = new AddVersionRequest(
                Base64.getEncoder().encodeToString("x".getBytes()),
                "u"
            );

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with large content")
        void validRequestWithLargeContentPassesValidation() {
            String largeContent = "x".repeat(10000);
            AddVersionRequest request = new AddVersionRequest(
                Base64.getEncoder().encodeToString(largeContent.getBytes()),
                "user@example.com"
            );

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Missing required fields")
    class MissingRequiredFields {

        @Test
        @DisplayName("should fail validation when content is null")
        void nullContentFailsValidation() {
            AddVersionRequest request = new AddVersionRequest(
                null,
                "user@example.com"
            );

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
        }

        @Test
        @DisplayName("should fail validation when content is empty")
        void emptyContentFailsValidation() {
            AddVersionRequest request = new AddVersionRequest(
                "",
                "user@example.com"
            );

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
        }

        @Test
        @DisplayName("should fail validation when content is whitespace only")
        void whitespaceContentFailsValidation() {
            AddVersionRequest request = new AddVersionRequest(
                "   \t\n  ",
                "user@example.com"
            );

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("content");
        }

        @Test
        @DisplayName("should fail validation when createdBy is null")
        void nullCreatedByFailsValidation() {
            AddVersionRequest request = new AddVersionRequest(
                Base64.getEncoder().encodeToString("content".getBytes()),
                null
            );

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("createdBy");
        }

        @Test
        @DisplayName("should fail validation when createdBy is empty")
        void emptyCreatedByFailsValidation() {
            AddVersionRequest request = new AddVersionRequest(
                Base64.getEncoder().encodeToString("content".getBytes()),
                ""
            );

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("createdBy");
        }

        @Test
        @DisplayName("should fail validation when createdBy is whitespace only")
        void whitespaceCreatedByFailsValidation() {
            AddVersionRequest request = new AddVersionRequest(
                Base64.getEncoder().encodeToString("content".getBytes()),
                "   "
            );

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("createdBy");
        }

        @Test
        @DisplayName("should fail validation with both fields null")
        void bothFieldsNullFailsValidation() {
            AddVersionRequest request = new AddVersionRequest(null, null);

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(2)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("content", "createdBy");
        }

        @Test
        @DisplayName("should fail validation with both fields blank")
        void bothFieldsBlankFailsValidation() {
            AddVersionRequest request = new AddVersionRequest("", "");

            Set<ConstraintViolation<AddVersionRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(2)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("content", "createdBy");
        }
    }
}
