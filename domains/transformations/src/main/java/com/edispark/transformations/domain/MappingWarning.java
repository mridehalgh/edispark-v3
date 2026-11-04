package com.edispark.transformations.domain;

import java.util.Objects;

/** Machine-readable evidence that a populated source value was not consumed by a mapping revision. */
public record MappingWarning(String code, String sourcePath, String disposition, String message) {
    public MappingWarning {
        code = requireText(code, "code");
        sourcePath = requireText(sourcePath, "sourcePath");
        disposition = requireText(disposition, "disposition");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
