package com.edispark.wireup.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;

final class DeskTriage {
    static final String QUESTION = "Triage this document.";
    private static final String RUNBOOK = loadRunbook();
    private static final Map<String, String> GUIDANCE = index(RUNBOOK);

    private DeskTriage() {}

    static String knowledge() { return RUNBOOK; }

    static JsonNode latest(JsonNode result) {
        return StreamSupport.stream(result.path("matches").spliterator(), false)
                .max(Comparator.comparing(item -> Instant.parse(item.path("updatedAt").asString(Instant.EPOCH.toString())))).orElse(null);
    }

    static String answer(JsonNode result, JsonNode transfer, boolean correctionAvailable) {
        if (!result.path("matches").isArray()) return "I couldn’t check the processing evidence. Open the document timeline or try again.";
        var exchange = latest(result);
        if (exchange == null) return result.hasNonNull("nextCursor")
                ? "No exchange appeared on the pages checked. Open the processing timeline for the full record."
                : "No exchange is recorded for this document. Check its validation and processing timeline before submitting.";
        var paragraphs = new ArrayList<String>();
        String reason = exchange.path("recordedReason").asString("");
        if (!reason.isBlank()) paragraphs.add(reason);

        String transferStatus = transfer == null ? "" : transfer.path("status").asString("");
        String disposition = transfer == null ? "" : transfer.path("receiptDisposition").asString("");
        String status = exchange.path("status").asString("");
        if ("RETRY_SCHEDULED".equals(transferStatus)) paragraphs.add(guidance("RETRY_SCHEDULED"));
        else if ("FAILED".equals(disposition)) {
            paragraphs.add(guidance("RECEIPT_REJECTED"));
        } else if ("AWAITING_MDN".equals(transferStatus)) {
            paragraphs.add(guidance("AWAITING_MDN"));
        } else if ("NEEDS_REVIEW".equals(transferStatus) || "FAILED".equals(transferStatus)) {
            var http = lastHttpStatus(transfer);
            if (http == 401 || http == 403) paragraphs.add(guidance("CONNECTION_ACCESS"));
            else if (http >= 400 && http < 500) paragraphs.add(guidance("CONNECTION_REQUEST"));
            else paragraphs.add(guidance("TRANSFER_REVIEW"));
        } else {
            for (var finding : exchange.path("findings")) {
                var advice = guidance(finding.path("code").asString(""));
                if (advice == null) advice = guidance(finding.path("owner").asString(""));
                if (advice != null && !paragraphs.contains(advice)) {
                    String field = finding.path("path").asString("");
                    if (guidance(finding.path("code").asString("")) == null && !field.isBlank() && !field.equals("/")) {
                        advice += " Recorded field " + field + ".";
                    }
                    paragraphs.add(advice);
                }
                if (paragraphs.size() >= 5) {
                    paragraphs.add("Open the timeline for the full list of findings.");
                    break;
                }
            }
            if (paragraphs.isEmpty() || paragraphs.size() == 1 && !reason.isBlank()) {
                var fallback = exchange.hasNonNull("transferId") && ("NEEDS_REVIEW".equals(status) || "FAILED".equals(status))
                        ? "TRANSFER_REVIEW" : status;
                var purpose = exchange.path("reasonCode").asString(fallback);
                paragraphs.add(Objects.requireNonNullElse(guidance(purpose), guidance("PROCESSING")));
            }
        }
        if (correctionAvailable) paragraphs.add("Use Open corrected draft to review the proposed correction.");
        if (result.hasNonNull("nextCursor")) paragraphs.add("This covers the exchange evidence checked so far.");
        return String.join("\n\n", paragraphs);
    }

    private static int lastHttpStatus(JsonNode transfer) {
        int status = 0;
        for (var attempt : transfer.path("attempts")) {
            if (attempt.hasNonNull("httpStatus")) status = attempt.path("httpStatus").asInt(0);
        }
        return status;
    }

    static String guidance(String code) { return GUIDANCE.get(code); }

    private static String loadRunbook() {
        try (var resource = DeskTriage.class.getResourceAsStream("/desk-triage.md")) {
            return new String(Objects.requireNonNull(resource, "Desk triage runbook").readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Map<String, String> index(String runbook) {
        var entries = new HashMap<String, String>();
        for (var section : runbook.split("(?m)^## ")) {
            if (section.startsWith("#")) continue;
            var lines = section.split("\n", 2);
            for (var code : lines[0].trim().split(" ")) entries.put(code, lines[1].trim());
        }
        return Map.copyOf(entries);
    }
}
