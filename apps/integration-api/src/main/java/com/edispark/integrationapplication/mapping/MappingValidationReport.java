package com.edispark.integrationapplication.mapping;

import java.util.List;

public record MappingValidationReport(String scope, boolean valid, List<Finding> findings) {
    public static MappingValidationReport of(String scope, List<Finding> findings) {
        return new MappingValidationReport(scope, findings.stream().noneMatch(finding -> "ERROR".equals(finding.severity())),
                List.copyOf(findings));
    }

    public record Finding(String severity, String code, String location, String message) {
        public static Finding error(String code, String location, String message) {
            return new Finding("ERROR", code, location, message);
        }
    }
}
