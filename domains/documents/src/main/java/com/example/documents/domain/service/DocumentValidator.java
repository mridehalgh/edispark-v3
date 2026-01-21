package com.example.documents.domain.service;

import com.example.documents.domain.model.Content;
import com.example.documents.domain.model.Format;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.ValidationResult;

/**
 * Domain service interface for validating documents against schemas.
 * 
 * <p>Implementations validate document content against schema definitions,
 * supporting various format combinations (e.g., XML/XSD, JSON/JSON_SCHEMA).
 * 
 * <p>This is a port in the hexagonal architecture - implementations are
 * provided in the infrastructure layer.
 */
public interface DocumentValidator {

    /**
     * Validates a document against a schema definition.
     *
     * @param document the document content to validate
     * @param schema the schema definition to validate against
     * @return the validation result containing success/failure status and any errors
     */
    ValidationResult validate(Content document, Content schema);

    /**
     * Checks whether this validator supports the given format combination.
     *
     * @param documentFormat the format of the document to validate
     * @param schemaFormat the format of the schema definition
     * @return true if this validator can handle the format combination
     */
    boolean supports(Format documentFormat, SchemaFormat schemaFormat);
}
