package com.edispark.transformations.domain;

import java.util.Map;

public record BusinessCode(String code, String label, Map<BusinessCodeSyntax, String> representations) {
    public BusinessCode {
        if (code == null || code.isBlank() || code.length() > 35) {
            throw new IllegalArgumentException("Business code must be between 1 and 35 characters");
        }
        if (label == null || label.isBlank() || label.length() > 160) {
            throw new IllegalArgumentException("Business code label must be between 1 and 160 characters");
        }
        code = code.strip();
        label = label.strip();
        representations = Map.copyOf(representations == null ? Map.of() : representations);
        if (!code.equals(representations.get(BusinessCodeSyntax.UBL_2_1))) {
            throw new IllegalArgumentException("Canonical business code must equal its UBL 2.1 representation");
        }
        if (representations.values().stream().anyMatch(BusinessCode::invalidRepresentation)) {
            throw new IllegalArgumentException("Business code representations must be bounded, non-blank strings");
        }
    }

    private static boolean invalidRepresentation(String value) {
        return value == null || value.isBlank() || value.length() > 100;
    }
}
