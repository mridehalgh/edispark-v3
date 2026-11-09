package com.edispark.documents.infrastructure.validation;
import com.edispark.documents.domain.model.*;
import com.edispark.documents.domain.service.DocumentValidator;
import java.util.List;

/** Fails closed for formats without an installed validator. */
public final class UnavailableDocumentValidator implements DocumentValidator {
    @Override public boolean supports(Format format, SchemaFormat schema) { return true; }
    @Override public ValidationResult validate(Content document, Content schema) {
        return ValidationResult.failure(List.of(ValidationError.of("/", "Validation is unavailable for this format. The document has not been approved for delivery.", "VALIDATION_UNAVAILABLE")));
    }
}
