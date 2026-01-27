package com.example.application.exception;

import com.example.documents.api.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global fallback exception handler for the application.
 *
 * <p>This handler catches any exceptions not handled by domain-specific exception handlers,
 * providing a consistent error response for unexpected errors. It uses
 * {@link Order#LOWEST_PRECEDENCE} to ensure it runs after all domain-specific handlers.</p>
 *
 * <p>Returns HTTP 500 Internal Server Error for all unhandled exceptions, with a generic
 * error message to avoid exposing internal implementation details.</p>
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles any unexpected exception not caught by domain-specific handlers.
     *
     * <p>Logs the full exception details for debugging while returning a generic error
     * message to the client to avoid exposing sensitive information.</p>
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with a generic error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(
                "INTERNAL_ERROR",
                "An unexpected error occurred"
            ));
    }
}
