package com.edispark.mftapplication.as2;

import com.edispark.mft.application.As2TransportException;
import com.edispark.mft.application.MftNotFoundException;
import com.edispark.mft.infrastructure.as2.As2AdmissionRejectedException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Profile("production")
@RestControllerAdvice(assignableTypes = ProductionAs2Controller.class)
public class ProductionAs2ExceptionHandler {
    @ExceptionHandler(As2AdmissionRejectedException.class)
    ResponseEntity<Void> tooManyRequests(As2AdmissionRejectedException ignored) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
    @ExceptionHandler(As2MessageTooLargeException.class)
    ResponseEntity<Void> messageTooLarge(As2MessageTooLargeException ignored) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class,
            MftNotFoundException.class, MissingRequestHeaderException.class})
    ResponseEntity<Void> invalidProtocolMessage(Exception ignored) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(As2TransportException.class)
    ResponseEntity<Void> dependencyUnavailable(As2TransportException exception) {
        return exception.retryable()
                ? ResponseEntity.status(503).build()
                : ResponseEntity.badRequest().build();
    }
}
