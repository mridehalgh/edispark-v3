package com.example.application.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

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
     * Simple error response record for application-level errors.
     */
    public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        Map<String, Object> details
    ) {
        public static ErrorResponse of(String code, String message) {
            return new ErrorResponse(code, message, Instant.now(), Map.of());
        }
    }

    /**
     * Handles HTTP method not supported errors.
     *
     * <p>Returns 405 Method Not Allowed when a client uses an unsupported HTTP method
     * (e.g., GET on a POST-only endpoint).</p>
     *
     * @param ex the exception
     * @return 405 Method Not Allowed with error details
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.debug("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponse.of(
                "METHOD_NOT_ALLOWED",
                ex.getMessage()
            ));
    }

    /**
     * Handles resource not found errors.
     *
     * <p>Returns 404 Not Found when Spring cannot find a handler for the requested path.</p>
     *
     * @param ex the exception
     * @return 404 Not Found with error details
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("No resource found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(
                "NOT_FOUND",
                "The requested resource was not found"
            ));
    }

    /**
     * Handles unsupported media type errors.
     *
     * <p>Returns 415 Unsupported Media Type when the request Content-Type is not supported.</p>
     *
     * @param ex the exception
     * @return 415 Unsupported Media Type with error details
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.debug("Media type not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ErrorResponse.of(
                "UNSUPPORTED_MEDIA_TYPE",
                ex.getMessage()
            ));
    }

    /**
     * Handles not acceptable media type errors.
     *
     * <p>Returns 406 Not Acceptable when the requested Accept header cannot be satisfied.</p>
     *
     * @param ex the exception
     * @return 406 Not Acceptable with error details
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.debug("Media type not acceptable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
            .body(ErrorResponse.of(
                "NOT_ACCEPTABLE",
                "The requested media type is not acceptable"
            ));
    }

    /**
     * Handles missing request parameter errors.
     *
     * <p>Returns 400 Bad Request when a required request parameter is missing.</p>
     *
     * @param ex the exception
     * @return 400 Bad Request with error details
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.debug("Missing request parameter: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "MISSING_PARAMETER",
                String.format("Required parameter '%s' is missing", ex.getParameterName())
            ));
    }

    /**
     * Handles method argument type mismatch errors.
     *
     * <p>Returns 400 Bad Request when a request parameter cannot be converted to the expected type.</p>
     *
     * @param ex the exception
     * @return 400 Bad Request with error details
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Type mismatch: {}", ex.getMessage());
        String message = String.format("Parameter '%s' has invalid value", ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "INVALID_PARAMETER",
                message
            ));
    }

    /**
     * Handles malformed JSON errors.
     *
     * <p>Returns 400 Bad Request when the request body cannot be parsed as JSON.</p>
     *
     * @param ex the exception
     * @return 400 Bad Request with error details
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Message not readable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(
                "MALFORMED_REQUEST",
                "Request body is malformed or cannot be read"
            ));
    }

    /**
     * Handles any unexpected exception not caught by domain-specific handlers.
     *
     * <p>Logs the full exception details for debugging while returning a generic error
     * message to the client to avoid exposing sensitive information.</p>
     *
     * <p>Ignores favicon requests to avoid cluttering logs with browser-generated noise.</p>
     *
     * @param ex the unexpected exception
     * @param request the HTTP request
     * @return 500 Internal Server Error with a generic error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError(Exception ex, HttpServletRequest request) {
        // Ignore favicon requests - browsers automatically request these
        if (isFaviconRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(
                "INTERNAL_ERROR",
                "An unexpected error occurred"
            ));
    }
    
    private boolean isFaviconRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith("/favicon.ico");
    }
}
