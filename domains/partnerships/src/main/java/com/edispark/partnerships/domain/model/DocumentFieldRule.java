package com.edispark.partnerships.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record DocumentFieldRule(
        String fieldKey,
        FieldRequirement requirement,
        String defaultValue,
        String fixedValue,
        List<String> allowedValues,
        BigDecimal minimum,
        BigDecimal maximum,
        String pattern,
        String message) {
    public DocumentFieldRule {
        if (fieldKey == null || fieldKey.isBlank()) throw new IllegalArgumentException("fieldKey must not be blank");
        requirement = requirement == null ? FieldRequirement.OPTIONAL : requirement;
        allowedValues = List.copyOf(allowedValues == null ? List.of() : allowedValues);
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
        if (requirement == FieldRequirement.HIDDEN && fixedValue != null && !fixedValue.isBlank()) {
            throw new IllegalArgumentException("A hidden field cannot also have a fixed value");
        }
        try {
            if (pattern != null && !pattern.isBlank()) Pattern.compile(pattern);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("pattern must be a valid regular expression", exception);
        }
    }
}
