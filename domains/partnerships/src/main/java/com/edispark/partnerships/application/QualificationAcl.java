package com.edispark.partnerships.application;

import java.util.UUID;

public interface QualificationAcl {
    boolean publishedMappingMatches(UUID mappingRevisionId, UUID sourceSchemaId, UUID targetSchemaId);
    boolean activeAs2ConnectionExists(UUID connectionId);
    default java.util.List<String> managedQualificationFailures(
            com.edispark.partnerships.domain.model.TradingAgreement agreement) {
        return java.util.List.of();
    }
    default java.util.List<String> referenceTableFailures(
            com.edispark.partnerships.domain.model.ExchangeCapability capability) {
        return capability.referenceTableRevisions().isEmpty() ? java.util.List.of()
                : java.util.List.of("Reference table qualification is unavailable.");
    }
    default RuleEvidence targetRuleEvidence(UUID mappingRevisionId) {
        return new RuleEvidence(false, "", java.util.List.of("Target qualification is unavailable for Order Response."));
    }
    default QualificationAcl persistedView() {
        throw new UnsupportedOperationException("Persisted qualification is unavailable");
    }

    record RuleEvidence(boolean valid, String identity, java.util.List<String> failures) { }
}
