package com.edispark.transformations.domain;

import java.util.List;

public record BusinessCodeListDefinition(BusinessCodeList id, String authority, String version,
        boolean partnerExtensionsAllowed, List<BusinessCode> values) {
    public BusinessCodeListDefinition {
        if (id == null) throw new IllegalArgumentException("Code list id is required");
        if (authority == null || authority.isBlank()) throw new IllegalArgumentException("Code list authority is required");
        version = version == null ? "current" : version.strip();
        values = List.copyOf(values == null ? List.of() : values);
        if (values.isEmpty()) throw new IllegalArgumentException("Code list values are required");
        if (values.stream().map(BusinessCode::code).distinct().count() != values.size()) {
            throw new IllegalArgumentException("Code list values must be unique");
        }
    }
}
