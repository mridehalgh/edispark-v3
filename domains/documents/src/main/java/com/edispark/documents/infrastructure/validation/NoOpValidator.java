package com.edispark.documents.infrastructure.validation;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.model.SchemaFormat;
import com.edispark.documents.domain.model.ValidationResult;
import com.edispark.documents.domain.service.DocumentValidator;

import java.util.logging.Logger;

/**
 * A stub validator that returns successful validation for all inputs.
 * 
 * <p>This is a placeholder implementation for initial development.
 * It logs a warning on each validation call to indicate that actual
 * validation is not being performed.
 * 
 * <p>Replace with format-specific validators (XsdValidator, JsonSchemaValidator)
 * for production use.
 */
public class NoOpValidator implements DocumentValidator {

    private static final Logger LOGGER = Logger.getLogger(NoOpValidator.class.getName());

    @Override
    public ValidationResult validate(Content document, Content schema) {
        LOGGER.warning("Validation is stubbed - returning success without actual validation. " +
                "Document format: " + document.format() + ", Schema hash: " + schema.hash());
        return ValidationResult.success();
    }

    @Override
    public boolean supports(Format documentFormat, SchemaFormat schemaFormat) {
        // Stub supports all format combinations
        return true;
    }
}
