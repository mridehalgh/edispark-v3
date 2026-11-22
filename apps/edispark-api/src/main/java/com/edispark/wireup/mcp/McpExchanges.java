package com.edispark.wireup.mcp;

import static com.edispark.wireup.mcp.McpResponseFields.*;

import com.edispark.documents.application.handler.DocumentNotFoundException;
import com.edispark.documents.domain.model.DocumentType;
import com.edispark.exchange.application.ExchangeEvidenceQuery;
import com.edispark.exchange.application.ExchangeEvidenceQuery.DateWindow;
import com.edispark.exchange.application.ExchangeEvidenceQuery.Page;
import com.edispark.exchange.application.ExchangeService;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.exchange.domain.ExchangeWarning;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.partnerships.application.PartnershipService;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
final class McpExchanges {
    private static final int FINDING_LIMIT = 40;
    private final ExchangeEvidenceQuery evidence;
    private final ExchangeService exchanges;
    private final PartnershipService partnerships;
    private final MftService transfers;
    private final McpDocuments documents;

    McpExchanges(ExchangeEvidenceQuery evidence, ExchangeService exchanges, PartnershipService partnerships, MftService transfers, McpDocuments documents) {
        this.evidence = evidence;
        this.exchanges = exchanges;
        this.partnerships = partnerships;
        this.transfers = transfers;
        this.documents = documents;
    }

    Map<String, Object> find(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        if (values.containsKey("exchangeId")) {
            if (values.size() != 1) throw new IllegalArgumentException("Use the exchange ID without search filters");
            return fields("matches", evidence.find(args.id("exchangeId")).stream().map(McpExchanges::summary).toList());
        }
        if (values.containsKey("documentSetId") || values.containsKey("documentId")) {
            if (!Set.of("documentSetId", "documentId", "cursor").containsAll(values.keySet())) {
                throw new IllegalArgumentException("Use the document identifiers without search filters");
            }
            var setId = args.id("documentSetId");
            var documentId = args.id("documentId");
            String binding = "find_exchanges/document/" + setId + "/" + documentId;
            return page(evidence.forSourceDocument(setId, documentId, args.uuidPosition(cursor, tenant, binding)),
                    cursor, tenant, binding, McpExchanges::summary);
        }
        var window = window(args);
        var partner = args.optionalId("partnerId");
        var reference = args.optionalText("sourceMessageReference");
        var status = args.optionalEnum("status", ExchangeStatus.class);
        var direction = args.optionalEnum("direction", ExchangeDirection.class);
        var type = args.optionalEnum("documentType", DocumentType.class);
        // Keep the binding used by existing exchange-search clients.
        String binding = String.join("\n", window.from().toString(), window.to().toString(),
                java.util.Objects.toString(partner, ""), java.util.Objects.toString(reference, ""),
                java.util.Objects.toString(status, ""), java.util.Objects.toString(direction, ""));
        if (type != null) binding += "\ndocumentType=" + type.name();
        var search = new ExchangeEvidenceQuery.Search(window, partner, reference, status, direction);
        var result = evidence.find(search, args.uuidPosition(cursor, tenant, binding));
        if (type == null) return page(result, cursor, tenant, binding, McpExchanges::summary);
        var matches = result.results().stream().map(this::withSourceDetails)
                .filter(row -> row.get("sourceDocument") instanceof Map<?, ?> source
                        && type.name().equals(source.get("documentType"))).toList();
        return page(new Page<>(matches, result.next()), cursor, tenant, binding, Function.identity());
    }

    Map<String, Object> documentExceptions(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var binding = args.cursorBinding("find_document_exceptions");
        var result = evidence.documentExceptions(window(args), args.optionalId("partnerId"), args.uuidPosition(cursor, tenant, binding));
        if (Boolean.TRUE.equals(values.get("groupByDocument"))) {
            var grouped = new LinkedHashMap<List<UUID>, ExchangeEvidenceQuery.DocumentException>();
            for (var item : result.results()) {
                var source = item.exchange().sourceDocument();
                grouped.merge(List.of(source.documentSetId(), source.documentId()), item, (first, second) ->
                        second.exchange().updatedAt().isAfter(first.exchange().updatedAt()) ? second : first);
            }
            result = new Page<>(List.copyOf(grouped.values()), result.next());
        }
        return page(result, cursor, tenant, binding, item -> {
            var summary = withSourceDetails(item.exchange());
            summary.putAll(fields("exceptionKind", item.kind()));
            return summary;
        });
    }

    Map<String, Object> interchange(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var binding = args.cursorBinding("trace_interchange");
        var result = evidence.forTransfer(args.id("transferId"), args.uuidPosition(cursor, tenant, binding));
        return page(result, cursor, tenant, binding, exchange -> fields("exchangeId", exchange.id(),
                "messageOrdinal", exchange.messageOrdinal(), "status", exchange.status(),
                "sourceDocument", document(exchange.sourceDocument()), "generatedDocument", document(exchange.generatedDocument())));
    }

    private Map<String, Object> withSourceDetails(EdiExchange exchange) {
        var result = summary(exchange);
        var source = exchange.sourceDocument();
        if (source != null && documents != null) {
            try {
                var details = documents.context(source.documentSetId(), source.documentId());
                var reference = document(source);
                reference.putAll(fields("documentType", details.get("documentType"), "businessReference", details.get("businessReference")));
                result.put("sourceDocument", reference);
            } catch (DocumentNotFoundException unavailable) {
                // The exchange remains evidence even when its source document is unavailable.
            }
        }
        return result;
    }

    private static DateWindow window(McpArguments args) {
        return new DateWindow(args.optionalInstant("from"), args.optionalInstant("to"));
    }

    private static <T> Map<String, Object> page(Page<T> result, McpCursor cursor, String tenant, String binding,
                                                 Function<T, Map<String, Object>> project) {
        return fields("matches", result.results().stream().map(project).toList(),
                "nextCursor", result.next() == null ? null : cursor.encode(result.next(), tenant, binding));
    }

    Map<String, Object> trace(Map<String, Object> args) {
        var exchange = evidence.find(new McpArguments(args).id("exchangeId")).orElseThrow(EvidenceMissing::new);
        var result = summary(exchange);
        result.putAll(fields("sourceDocument", document(exchange.sourceDocument()),
                "generatedDocument", document(exchange.generatedDocument()),
                "mappingReport", document(exchange.mappingReport()),
                "timeline", tail(exchange.stages()).stream().map(stage ->
                        fields("status", stage.status(), "occurredAt", stage.occurredAt())).toList(),
                "findings", exchange.warnings().stream().limit(FINDING_LIMIT)
                        .map(McpExchanges::finding).toList(),
                "findingsTruncated", exchange.warnings().size() > FINDING_LIMIT,
                "functionalAcknowledgement", "NOT_RECORDED", "businessAcceptance", "NOT_RECORDED"));
        try {
            var capability = capability(exchange);
            result.putAll(fields("mappingRevisionId", capability.mappingRevisionId(),
                    "acknowledgementPolicy", capability.acknowledgementPolicy()));
        } catch (EvidenceMissing | IllegalArgumentException missing) {
            result.put("routeEvidence", "UNAVAILABLE");
        }
        if (exchange.mftTransferId() == null) {
            result.put("transportReceipt", "NOT_RECORDED");
            return result;
        }
        try {
            var transfer = transfers.getTransfer(new TransferId(exchange.mftTransferId()));
            result.putAll(fields("transfer", fields("id", transfer.id().value(), "status", transfer.status(),
                            "updatedAt", transfer.updatedAt(), "attempts", tail(transfer.attempts()).stream()
                                    .map(attempt -> fields("number", attempt.number(), "startedAt", attempt.startedAt(),
                                            "completedAt", attempt.completedAt(), "httpStatus", attempt.httpStatus())).toList()),
                    "transportReceipt", transfer.hasDeliveredReceipt() ? "RECORDED" : "NOT_RECORDED"));
        } catch (com.edispark.mft.application.MftNotFoundException missing) {
            result.put("transportReceipt", "UNAVAILABLE");
        }
        return result;
    }

    Map<String, Object> readiness(Map<String, Object> args) {
        var source = documentArgs(args);
        var report = exchanges.checkReadiness(new McpArguments(args).id("agreementId"), new McpArguments(args).id("capabilityId"),
                source.documentSetId(), source.documentId(), source);
        return fields("ready", report.ready(), "checkedAt", report.checkedAt(), "sourceDocument", document(report.sourceDocument()),
                "agreementId", report.agreementId(), "agreementRevision", report.agreementRevision(),
                "capabilityId", report.capabilityId(), "mappingRevisionId", report.mappingRevisionId(),
                "documentProfileRevisionId", report.documentProfileRevisionId(),
                "findings", report.issues().stream().limit(FINDING_LIMIT).map(issue ->
                        fields("code", safeCode(issue.code()), "path", safePath(issue.path()),
                                "owner", safeCode(issue.owner()), "severity", safeCode(issue.severity()))).toList(),
                "findingsTruncated", report.issues().size() > FINDING_LIMIT);
    }

    private com.edispark.partnerships.domain.model.ExchangeCapability capability(EdiExchange exchange) {
        if (exchange.agreementId() == null || exchange.capabilityId() == null) throw new EvidenceMissing();
        var agreement = partnerships.getAgreement(exchange.agreementId());
        if (agreement.revision() != exchange.agreementRevision()) throw new EvidenceMissing();
        return agreement.capabilities().stream().filter(item -> item.id().equals(exchange.capabilityId()))
                .findFirst().orElseThrow(EvidenceMissing::new);
    }

    private static Map<String, Object> summary(EdiExchange exchange) {
        var result = fields("exchangeId", exchange.id(), "direction", exchange.direction(), "partnerId", exchange.partnerId(),
                "agreementId", exchange.agreementId(), "agreementRevision", exchange.agreementRevision(),
                "capabilityId", exchange.capabilityId(), "status", exchange.status(),
                "messageOrdinal", exchange.messageOrdinal(), "transferId", exchange.mftTransferId(),
                "sourceDocument", document(exchange.sourceDocument()), "generatedDocument", document(exchange.generatedDocument()),
                "messageReference", exchange.sourceMessageReference(),
                "createdAt", exchange.createdAt(), "updatedAt", exchange.updatedAt(),
                "workspacePath", "/documents");
        if (exchange.status() == ExchangeStatus.NEEDS_REVIEW) {
            // Only these system-generated stage details explicitly confirm a stop before transport.
            exchange.stages().stream().map(stage -> stage.detail()).filter(detail ->
                    "Partner readiness failed. Nothing was submitted to transport.".equals(detail)
                    || "Mapping stopped before transport because populated source fields were not represented".equals(detail))
                    .findFirst().ifPresent(detail -> result.put("recordedReason", detail));
            exchange.stages().stream().map(stage -> stage.detail()).filter(java.util.Objects::nonNull)
                    .filter(detail -> detail.startsWith("MESSAGE_REJECTED:")).findFirst()
                    .ifPresent(detail -> result.put("reasonCode", "MESSAGE_REJECTED"));
            if ("Mapping stopped before transport because populated source fields were not represented".equals(result.get("recordedReason"))) {
                result.put("reasonCode", "MAPPING");
            }
            result.put("findings", exchange.warnings().stream().limit(FINDING_LIMIT)
                    .map(McpExchanges::finding).toList());
        }
        return result;
    }

    private static DocumentReference documentArgs(Map<String, Object> values) {
        var args = new McpArguments(values);
        return new DocumentReference(args.id("documentSetId"), args.id("documentId"), args.id("representationId"), args.text("contentHash"));
    }

    private static Map<String, Object> document(DocumentReference reference) {
        return reference == null ? null : fields("documentSetId", reference.documentSetId(), "documentId", reference.documentId(),
                "representationId", reference.representationId(), "contentHash", reference.contentHash());
    }

    private static Map<String, Object> finding(ExchangeWarning warning) {
        var finding = fields("code", safeCode(warning.code()), "path", safePath(warning.sourcePath()));
        String code = safeCode(warning.code());
        String owner = "BQ_VENDOR_NUMBER_REQUIRED".equals(code) ? "DOCUMENT" : warning.disposition();
        if (Set.of("DOCUMENT", "CONFIGURATION", "MAPPING").contains(java.util.Objects.toString(owner, ""))) {
            String advice = java.util.Objects.requireNonNullElse(DeskTriage.guidance(code), DeskTriage.guidance(owner));
            boolean correctable = "DOCUMENT".equals(owner) && !Set.of("DOCUMENT_NOT_FINAL", "BQ_ACK_DECISION_UNSUPPORTED",
                    "RESPONSE_STATUS_CONFLICT").contains(code);
            finding.putAll(fields("owner", owner, "operatorAction", advice, "recovery", correctable ? "CORRECT_DOCUMENT" : null));
        }
        return finding;
    }

    private static <T> List<T> tail(List<T> values) {
        return values.subList(Math.max(0, values.size() - FINDING_LIMIT), values.size());
    }
}
