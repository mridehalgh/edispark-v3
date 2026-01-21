package com.example.documents.application.handler;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.ValidationResult;
import com.example.documents.domain.service.DocumentValidator;

/**
 * Stub implementation of DocumentValidator for testing.
 */
class StubDocumentValidator implements DocumentValidator {

    private ValidationResult nextResult = ValidationResult.success();
    private final Format supportedDocFormat;
    private final SchemaFormat supportedSchemaFormat;

    StubDocumentValidator(Format supportedDocFormat, SchemaFormat supportedSchemaFormat) {
        this.supportedDocFormat = supportedDocFormat;
        this.supportedSchemaFormat = supportedSchemaFormat;
    }

    @Override
    public ValidationResult validate(Content document, Content schema) {
        return nextResult;
    }

    @Override
    public boolean supports(Format documentFormat, SchemaFormat schemaFormat) {
        return documentFormat == supportedDocFormat && schemaFormat == supportedSchemaFormat;
    }

    public void setNextResult(ValidationResult result) {
        this.nextResult = result;
    }
}
