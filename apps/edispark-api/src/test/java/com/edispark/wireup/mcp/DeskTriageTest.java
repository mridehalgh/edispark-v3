package com.edispark.wireup.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.mft.domain.model.TransferStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DeskTriageTest {
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(ExchangeStatus.class)
    void providesAnEvidenceAppropriateNextStepForEveryExchangeState(ExchangeStatus status) {
        String advice = DeskTriage.answer(exchange(Map.of("status", status.name())), null, false);
        String expected = switch (status) {
            case FAILED -> "last recorded stage";
            case NEEDS_REVIEW -> "latest finding";
            case AWAITING_RECEIPT -> "expected receipt";
            case DELIVERED -> "retailer acceptance";
            default -> "latest stage";
        };
        assertThat(advice).contains(expected).doesNotContain("Open corrected draft");
    }

    @ParameterizedTest
    @EnumSource(TransferStatus.class)
    void coversEachTransferStatusWithoutPromisingAResend(TransferStatus status) {
        var transfer = json.valueToTree(Map.of("status", status.name()));
        String advice = DeskTriage.answer(exchange(Map.of("status", "NEEDS_REVIEW", "transferId", "test")), transfer, false);
        String expected = switch (status) {
            case RETRY_SCHEDULED -> "already scheduled";
            case AWAITING_MDN -> "expected receipt";
            default -> "linked transfer";
        };
        assertThat(advice).contains(expected).doesNotContain("I’ve resent", "Open corrected draft");
    }

    @ParameterizedTest
    @CsvSource({
            "BQ_VENDOR_NUMBER_REQUIRED, DOCUMENT, leading zeros",
            "BQ_ACK_DECISION_UNSUPPORTED, DOCUMENT, full acceptance only",
            "BQ_DELIVERY_DATE_REQUIRED, DOCUMENT, purchase order",
            "BQ_ITEM_EAN_REQUIRED, DOCUMENT, product EAN",
            "BQ_LINE_REFERENCE_DUPLICATE, DOCUMENT, line reference",
            "BQ_ORDER_VERSION_REFERENCE, DOCUMENT, line reference",
            "BQ_BUYER_IDENTITY, DOCUMENT, GLNs",
            "BQ_IDENTIFIER_SCHEME, DOCUMENT, GLNs",
            "BQ_CUSTOMER_NAME, DOCUMENT, confirmed names",
            "SOURCE_IDENTIFIER_INVALID, DOCUMENT, identifier schemes",
            "TARGET_REQUIRED, DOCUMENT, required values",
            "TARGET_REPRESENTATION, DOCUMENT, field length",
            "TARGET_LINE_COUNT, DOCUMENT, required values",
            "BQ_ITEM_SCHEME, DOCUMENT, partner profile",
            "BQ_ORDER_UNIT_REQUIRED, DOCUMENT, quantity",
            "BQ_WHOLE_QUANTITY_REQUIRED, DOCUMENT, quantity",
            "RESPONSE_STATUS_CONFLICT, DOCUMENT, full acceptance only",
            "DOCUMENT_NOT_FINAL, DOCUMENT, finalise",
            "PARTNER_UNAVAILABLE, CONFIGURATION, active partner",
            "PARTNER_MISMATCH, CONFIGURATION, pinned revisions",
            "TARGET_QUALIFICATION_STALE, CONFIGURATION, requalify",
            "PROFILE_UNAVAILABLE, CONFIGURATION, pinned revisions",
            "MAPPING_VALIDATION_FAILED, MAPPING, mapping administrator",
            "VALIDATION_UNAVAILABLE, CONFIGURATION, Run readiness again",
            "NEW_DOCUMENT_FINDING, DOCUMENT, verified source records",
            "NEW_MAPPING_FINDING, MAPPING, mapping administrator",
            "NEW_CONFIGURATION_FINDING, CONFIGURATION, workspace administrator",
            "UNKNOWN, UNKNOWN, latest finding"
    })
    void usesDocumentedAdviceForKnownCodesAndSafeFallbacksForNewCodes(String code, String owner, String expected) {
        var result = exchange(Map.of("status", "NEEDS_REVIEW",
                "findings", List.of(Map.of("code", code, "owner", owner))));
        assertThat(DeskTriage.answer(result, null, false)).contains(expected).doesNotContain("Open corrected draft");
    }

    @Test
    void showsTheActionOnlyWhenPreparedAndKeepsOlderFailuresOutOfTheDiagnosis() {
        var result = json.valueToTree(Map.of("matches", List.of(
                Map.of("status", "NEEDS_REVIEW", "updatedAt", "2026-11-19T10:00:00Z"),
                Map.of("status", "DELIVERED", "updatedAt", "2026-11-20T10:00:00Z"))));
        assertThat(DeskTriage.answer(result, null, false)).contains("retailer acceptance").doesNotContain("latest finding");
        var rejected = json.valueToTree(Map.of("receiptDisposition", "FAILED"));
        assertThat(DeskTriage.answer(exchange(Map.of("status", "NEEDS_REVIEW")), rejected, false))
                .contains("message integrity check");
        assertThat(DeskTriage.answer(exchange(Map.of("status", "NEEDS_REVIEW")), null, true))
                .contains("Open corrected draft");
    }

    @Test
    void distinguishesAccessAndEndpointErrorsFromTransientRetries() {
        var blocked = exchange(Map.of("status", "NEEDS_REVIEW", "transferId", "sample"));
        var denied = json.valueToTree(Map.of("status", "NEEDS_REVIEW", "attempts", List.of(Map.of("httpStatus", 403))));
        var endpoint = json.valueToTree(Map.of("status", "FAILED", "attempts", List.of(Map.of("httpStatus", 404))));
        assertThat(DeskTriage.answer(blocked, denied, false)).contains("connection permissions");
        assertThat(DeskTriage.answer(blocked, endpoint, false)).contains("partner endpoint");
        assertThat(DeskTriage.answer(exchange(Map.of("status", "NEEDS_REVIEW", "reasonCode", "MESSAGE_REJECTED")), null, false))
                .contains("corrected interchange");
    }

    @Test
    void distinguishesMissingEvidenceFromAnIncompleteOrFailedLookup() {
        assertThat(DeskTriage.answer(json.valueToTree(Map.of("matches", List.of())), null, false)).contains("No exchange is recorded");
        assertThat(DeskTriage.answer(json.valueToTree(Map.of("matches", List.of(), "nextCursor", "more")), null, false))
                .contains("pages checked");
        assertThat(DeskTriage.answer(json.valueToTree(Map.of("code", "EVIDENCE_UNAVAILABLE")), null, false)).contains("couldn’t check");
    }

    private JsonNode exchange(Map<String, Object> row) { return json.valueToTree(Map.of("matches", List.of(row))); }
}
