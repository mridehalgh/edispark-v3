package com.edispark.wireup.mcp;

import static com.edispark.wireup.mcp.McpResponseFields.*;

import com.edispark.partnerships.application.PartnershipService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class McpPartnerships {
    private static final int FINDING_LIMIT = 40;
    private final PartnershipService partnerships;

    McpPartnerships(PartnershipService partnerships) {
        this.partnerships = partnerships;
    }

    Map<String, Object> route(Map<String, Object> args) {
        var agreement = partnerships.getAgreement(new McpArguments(args).id("agreementId"));
        var capability = agreement.capabilities().stream().filter(item -> item.id().equals(new McpArguments(args).id("capabilityId")))
                .findFirst().orElseThrow(EvidenceMissing::new);
        return fields("agreementId", agreement.id(), "agreementRevision", agreement.revision(), "partnerId", agreement.partnerId(),
                "agreementStatus", agreement.status(), "capabilityId", capability.id(), "documentType", capability.documentType(),
                "direction", capability.direction(), "standard", capability.standard(), "release", safePath(capability.release()),
                "messageType", safeCode(capability.messageType()), "mappingRevisionId", capability.mappingRevisionId(),
                "documentProfileRevisionId", capability.documentProfileRevisionId(), "acknowledgementPolicy", capability.acknowledgementPolicy(),
                "qualification", agreement.qualification() == null ? "NOT_RECORDED"
                        : agreement.qualification().passed() ? "PASSED" : "FAILED");
    }

    Map<String, Object> documentProfile(Map<String, Object> args) {
        var profile = partnerships.persistedDocumentProfile(new McpArguments(args).id("documentProfileRevisionId"));
        return fields("documentProfileRevisionId", profile.id(), "familyId", profile.familyId(),
                "partnerId", profile.partnerId(), "revision", profile.revision(), "status", profile.status(),
                "direction", profile.direction(), "documentType", profile.documentType(),
                "ssccGenerationMode", profile.ssccGenerationMode(),
                "fieldRules", profile.fieldRules().stream().limit(FINDING_LIMIT).map(rule ->
                        fields("fieldKey", safePath(rule.fieldKey()), "requirement", rule.requirement(),
                                "hasFixedValue", rule.fixedValue() != null, "hasDefaultValue", rule.defaultValue() != null,
                                "hasCodeRestriction", !rule.allowedValues().isEmpty())).toList(),
                "fieldRulesTruncated", profile.fieldRules().size() > FINDING_LIMIT);
    }

    Map<String, Object> previewQualification(Map<String, Object> args) {
        UUID agreementId = new McpArguments(args).id("agreementId");
        var report = partnerships.previewQualification(agreementId);
        return fields("agreementId", agreementId, "passed", report.passed(), "evaluatedAt", report.completedAt(),
                "checks", report.checks().size(), "failures", report.failures().size());
    }

}
