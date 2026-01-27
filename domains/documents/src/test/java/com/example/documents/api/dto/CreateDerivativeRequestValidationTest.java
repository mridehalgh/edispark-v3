package com.example.documents.api.dto;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.documents.domain.model.Format;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Unit tests for {@link CreateDerivativeRequest} validation.
 * 
 * <p>Validates: Requirements 9.4 - THE Request_DTO for creating derivatives SHALL require
 * source version number and target format</p>
 */
@DisplayName("CreateDerivativeRequest Validation")
class CreateDerivativeRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private CreateDerivativeRequest validRequest() {
        return new CreateDerivativeRequest(1, Format.PDF);
    }

    @Nested
    @DisplayName("Valid requests")
    class ValidRequests {

        @Test
        @DisplayName("should pass validation with version number 1 and valid format")
        void validRequestWithVersionOnePassesValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(1, Format.PDF);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with higher version numbers")
        void validRequestWithHigherVersionPassesValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(100, Format.XML);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with JSON format")
        void validRequestWithJsonFormatPassesValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(5, Format.JSON);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should pass validation with EDI format")
        void validRequestWithEdiFormatPassesValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(2, Format.EDI);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Invalid source version number")
    class InvalidSourceVersionNumber {

        @Test
        @DisplayName("should fail validation when sourceVersionNumber is 0")
        void zeroVersionNumberFailsValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(0, Format.PDF);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("sourceVersionNumber");
        }

        @Test
        @DisplayName("should fail validation when sourceVersionNumber is negative")
        void negativeVersionNumberFailsValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(-1, Format.PDF);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("sourceVersionNumber");
        }

        @Test
        @DisplayName("should fail validation when sourceVersionNumber is large negative")
        void largeNegativeVersionNumberFailsValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(-100, Format.XML);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("sourceVersionNumber");
        }
    }

    @Nested
    @DisplayName("Missing required fields")
    class MissingRequiredFields {

        @Test
        @DisplayName("should fail validation when targetFormat is null")
        void nullTargetFormatFailsValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(1, null);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(1)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("targetFormat");
        }

        @Test
        @DisplayName("should fail validation with invalid version and null format")
        void invalidVersionAndNullFormatFailsValidation() {
            CreateDerivativeRequest request = new CreateDerivativeRequest(0, null);

            Set<ConstraintViolation<CreateDerivativeRequest>> violations = validator.validate(request);

            assertThat(violations)
                .hasSize(2)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("sourceVersionNumber", "targetFormat");
        }
    }
}
