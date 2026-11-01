package com.edispark.partnerships.domain.model;

import java.util.Locale;

public record BusinessIdentity(String scheme, String value) {
    public BusinessIdentity {
        scheme = requireText(scheme, "scheme").toUpperCase(Locale.ROOT);
        value = requireText(value, "value");
        if (scheme.length() > 40 || value.length() > 128) {
            throw new IllegalArgumentException("Business identity is too long");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
