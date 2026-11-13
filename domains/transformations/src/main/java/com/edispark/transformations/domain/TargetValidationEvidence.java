package com.edispark.transformations.domain;

import java.util.List;

/** Hashes and rule identity only. Customer fixture payloads are not retained here. */
public record TargetValidationEvidence(String ruleSet, String ruleFingerprint, String mappingHash, List<String> fixtureHashes) {
    public TargetValidationEvidence { fixtureHashes = List.copyOf(fixtureHashes); }
}
