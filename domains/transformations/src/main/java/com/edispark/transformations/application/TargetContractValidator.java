package com.edispark.transformations.application;

import java.util.List;
import java.util.UUID;

/** Caller-owned, side-effect-free source/target contract boundary. */
public interface TargetContractValidator {
    Result validate(UUID sourceSchemaId, UUID targetSchemaId, byte[] source, byte[] mapped);
    record Finding(String code, String path, String message, boolean sourceLocation) {}
    record Result(String ruleSet, String fingerprint, List<Finding> findings) {
        public Result { findings = List.copyOf(findings); }
        public boolean valid() { return findings.isEmpty(); }
    }
    static TargetContractValidator unavailable() {
        return (source, target, input, output) -> new Result("unavailable", "unavailable",
                List.of(new Finding("TARGET_RULES_UNAVAILABLE", "/", "Target validation is not available for this route.", false)));
    }
}
