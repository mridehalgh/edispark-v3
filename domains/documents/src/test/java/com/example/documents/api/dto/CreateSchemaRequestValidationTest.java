package com.example.documents.api.dto;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.documents.domain.model.SchemaFormat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Unit tests for {@link CreateSchemaRequest} validation.
 * 
 * <p>Validates: Requirements 9.5 - THE Request_DTO for creating schemas SHALL require
 * name and format</p>
 */
@DisplayName("CreateSchemaRequest Validation")
class CreateSchemaRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private CreateSchemaRequest validRequest() {
        return new CreateSchemaRequest(
            "Invoice Schema",
            SchemaFormat.XSD
        );
    }

    @Nested
    @DisplayName("Valid requests")
    class ValidRequests {

        @Test
        @DisplayName("should pass validation with all required fields")
        void validRequestPassesValidation() {
            CreateSchemaRequest request = validRequest();

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with XSD format")
        void validRequestWithXsdFormatPassesValidation() {
            CreateSchemaRequest request = new CreateSchemaRequest(
                "XSD Schema",
                SchemaFormat.XSD
            );

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with JSON_SCHEMA format")
        void validRequestWithJsonSchemaFormatPassesValidation() {
            CreateSchemaRequest request = new CreateSchemaRequest(
                "JSON Schema",
                SchemaFormat.JSON_SCHEMA
            );

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with RELAXNG format")
        void validRequestWithRelaxngFormatPassesValidation() {
            CreateSchemaRequest request = new CreateSchemaRequest(
                "RelaxNG Schema",
                SchemaFormat.RELAXNG
            );

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Missing required fields")
    class MissingRequiredFields {

        @Test
        @DisplayName("should fail validation when name is null")
        void nullNameFailsValidation() {
            CreateSchemaRequest request = new CreateSchemaRequest(
                null,
                SchemaFormat.XSD
            );

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("name");
        }

        @Test
        @DisplayName("should fail validation when name is blank")
        void blankNameFailsValidation() {
            CreateSchemaRequest request = new CreateSchemaRequest(
                "   ",
                SchemaFormat.XSD
            );

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("name");
        }

        @Test
        @DisplayName("should fail validation when name is empty")
        void emptyNameFailsValidation() {
            CreateSchemaRequest request = new CreateSchemaRequest(
                "",
                SchemaFormat.XSD
            );

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("name");
        }

        @Test
        @DisplayName("should fail validation when format is null")
        void nullFormatFailsValidation() {
            CreateSchemaRequest request = new CreateSchemaRequest(
                "Invoice Schema",
                null
            );

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("format");
        }

        @Test
        @DisplayName("should fail validation with multiple missing required fields")
        void multipleNullFieldsFailValidation() {
            CreateSchemaRequest request = new CreateSchemaRequest(
                null,
                null
            );

            Set<ConstraintViolation<CreateSchemaRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(2)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                    "name",
                    "format"
                );
        }
    }
}
