package com.edispark.mft.api.rest;

import com.edispark.mft.api.dto.MftErrorResponse;
import com.edispark.mft.application.MftNotFoundException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.edispark.mft")
public class MftExceptionHandler {
    @ExceptionHandler(MftNotFoundException.class)
    public ResponseEntity<MftErrorResponse> notFound(MftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(MftErrorResponse.of("MFT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<MftErrorResponse> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(MftErrorResponse.of("INVALID_MFT_REQUEST", "The request is invalid"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<MftErrorResponse> storage(IOException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(MftErrorResponse.of("MFT_STORAGE_UNAVAILABLE", "Transfer storage is unavailable"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<MftErrorResponse> invalidState(IllegalStateException exception) {
        String message = exception.getMessage() == null ? "The operation is not allowed in the current state" : exception.getMessage();
        String code = message.contains("assigned") || message.contains("referenced") || message.contains("unused draft")
                ? "MFT_CONFIGURATION_IN_USE" : "MFT_STATE_CONFLICT";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MftErrorResponse.of(code, message));
    }
}
