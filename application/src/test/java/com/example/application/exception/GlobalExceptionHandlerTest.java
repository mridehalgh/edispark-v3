package com.example.application.exception;

import com.example.documents.api.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleUnexpectedError_shouldReturn500InternalServerError() {
        // Given
        Exception exception = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedError(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void handleUnexpectedError_shouldReturnErrorResponseWithInternalErrorCode() {
        // Given
        Exception exception = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedError(exception);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void handleUnexpectedError_shouldReturnGenericErrorMessage() {
        // Given
        Exception exception = new RuntimeException("Sensitive internal details");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedError(exception);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        // Verify the sensitive details are not exposed
        assertThat(response.getBody().message()).doesNotContain("Sensitive internal details");
    }

    @Test
    void handleUnexpectedError_shouldIncludeTimestamp() {
        // Given
        Exception exception = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedError(exception);

        // Then
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleUnexpectedError_shouldHandleNullPointerException() {
        // Given
        Exception exception = new NullPointerException("Null value encountered");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedError(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void handleUnexpectedError_shouldHandleIllegalStateException() {
        // Given
        Exception exception = new IllegalStateException("Invalid state");

        // When
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedError(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }
}
