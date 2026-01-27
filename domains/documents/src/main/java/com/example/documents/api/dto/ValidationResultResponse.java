package com.example.documents.api.dto;

import java.util.List;

/**
 * Response DTO representing the result of document validation against a schema.
 *
 * <p>Contains a validity flag along with lists of errors and warnings discovered
 * during validation. When {@code valid} is {@code true}, the errors list will be
 * empty but warnings may still be present.
 *
 * @param valid {@code true} if the document passed validation, {@code false} otherwise
 * @param errors the list of validation errors that caused the document to fail validation
 * @param warnings the list of validation warnings that do not cause validation failure
 */
public record ValidationResultResponse(
    boolean valid,
    List<ValidationErrorResponse> errors,
    List<ValidationWarningResponse> warnings
) {
    /**
     * Represents a validation error with its location and description.
     *
     * @param path the JSON path or XPath to the element that failed validation
     * @param message the description of the validation error
     */
    public record ValidationErrorResponse(String path, String message) {}

    /**
     * Represents a validation warning with its location and description.
     *
     * @param path the JSON path or XPath to the element that triggered the warning
     * @param message the description of the validation warning
     */
    public record ValidationWarningResponse(String path, String message) {}
}
