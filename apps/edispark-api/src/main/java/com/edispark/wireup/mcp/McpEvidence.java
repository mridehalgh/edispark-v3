package com.edispark.wireup.mcp;

import com.edispark.exchange.application.ExchangeService;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.partnerships.application.PartnershipService;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.transformations.application.MappingService;
import com.edispark.transformations.application.RetainedMappingSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
final class McpEvidence {
    private static final int PAGE_SIZE = 32;
    private static final int FINDING_LIMIT = 40;

    private final ExchangeService exchanges;
    private final PartnershipService partnerships;
    private final MftService transfers;
    private final MappingService mappings;
    private final RetainedMappingSource sources;

    McpEvidence(ExchangeService exchanges, PartnershipService partnerships, MftService transfers,
                MappingService mappings, RetainedMappingSource sources) {
        this.exchanges = exchanges;
        this.partnerships = partnerships;
        this.transfers = transfers;
        this.mappings = mappings;
        this.sources = sources;
    }

    Map<String, Object> find(Map<String, Object> args, McpCursor cursor, String tenant) {
        if (args.containsKey("exchangeId")) {
            if (args.size() != 1) throw new IllegalArgumentException();
            return Map.of("matches", exchanges.persisted(id(args, "exchangeId")).stream().map(McpEvidence::summary).toList());
        }
        Instant from = Instant.parse(text(args, "from"));
        Instant to = Instant.parse(text(args, "to"));
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException();
        }
        UUID partner = args.containsKey("partnerId") ? id(args, "partnerId") : null;
        String reference = (String) args.get("sourceMessageReference");
        String status = (String) args.get("status");
        String direction = (String) args.get("direction");
        String filter = String.join("\n", from.toString(), to.toString(), Objects.toString(partner, ""),
                Objects.toString(reference, ""), Objects.toString(status, ""), Objects.toString(direction, ""));
        UUID after = args.containsKey("cursor") ? cursor.decode(text(args, "cursor"), tenant, filter) : null;
        var page = exchanges.persistedPage(after, PAGE_SIZE);
        var matches = page.exchanges().stream().filter(item -> !item.createdAt().isBefore(from) && item.createdAt().isBefore(to))
                .filter(item -> partner == null || partner.equals(item.partnerId()))
                .filter(item -> reference == null || reference.equals(item.sourceMessageReference()))
                .filter(item -> status == null || item.status() == ExchangeStatus.valueOf(status))
                .filter(item -> direction == null || item.direction() == ExchangeDirection.valueOf(direction))
                .map(McpEvidence::summary).toList();
        return fields("matches", matches, "nextCursor", page.next() == null ? null : cursor.encode(page.next(), tenant, filter));
    }

    Map<String, Object> trace(Map<String, Object> args) {
        var exchange = exchanges.persisted(id(args, "exchangeId")).orElseThrow(EvidenceMissing::new);
        var result = summary(exchange);
        result.putAll(fields("sourceDocument", document(exchange.sourceDocument()),
                "generatedDocument", document(exchange.generatedDocument()),
                "mappingReport", document(exchange.mappingReport()),
                "timeline", tail(exchange.stages()).stream().map(stage ->
                        fields("status", stage.status(), "occurredAt", stage.occurredAt())).toList(),
                "findings", exchange.warnings().stream().limit(FINDING_LIMIT)
                        .map(warning -> finding(warning.code(), warning.sourcePath())).toList(),
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
                    "transportReceipt", transfer.status() == TransferStatus.DELIVERED && transfer.mdnDisposition() != null
                            ? "RECORDED" : "NOT_RECORDED"));
        } catch (com.edispark.mft.application.MftNotFoundException missing) {
            result.put("transportReceipt", "UNAVAILABLE");
        }
        return result;
    }

    Map<String, Object> route(Map<String, Object> args) {
        var agreement = partnerships.getAgreement(id(args, "agreementId"));
        var capability = agreement.capabilities().stream().filter(item -> item.id().equals(id(args, "capabilityId")))
                .findFirst().orElseThrow(EvidenceMissing::new);
        return fields("agreementId", agreement.id(), "agreementRevision", agreement.revision(), "partnerId", agreement.partnerId(),
                "agreementStatus", agreement.status(), "capabilityId", capability.id(), "documentType", capability.documentType(),
                "direction", capability.direction(), "standard", capability.standard(), "release", safePath(capability.release()),
                "messageType", safeCode(capability.messageType()), "mappingRevisionId", capability.mappingRevisionId(),
                "documentProfileRevisionId", capability.documentProfileRevisionId(), "acknowledgementPolicy", capability.acknowledgementPolicy(),
                "qualification", agreement.qualification() == null ? "NOT_RECORDED"
                        : agreement.qualification().passed() ? "PASSED" : "FAILED");
    }

    Map<String, Object> preview(Map<String, Object> args) {
        UUID revision = id(args, "mappingRevisionId");
        var source = args.containsKey("representationId") ? documentArgs(args) : null;
        if (source == null && !args.keySet().equals(java.util.Set.of("mappingRevisionId", "input", "expected"))
                || source != null && !args.keySet().equals(java.util.Set.of("mappingRevisionId", "documentSetId",
                        "documentId", "representationId", "contentHash"))) throw new IllegalArgumentException();
        var preview = source == null
                ? mappings.preview(revision, bytes(args, "input"), bytes(args, "expected"))
                : mappings.previewRetained(revision, new RetainedMappingSource.Reference(source.documentSetId(),
                        source.documentId(), source.representationId(), source.contentHash()), sources);
        var findings = Stream.concat(preview.warnings().stream().map(warning -> finding(warning.code(), warning.sourcePath())),
                preview.findings().stream().map(finding -> finding(finding.code(), finding.path()))).limit(FINDING_LIMIT).toList();
        return fields("mappingRevisionId", revision, "mode", source == null ? "EXPECTED_OUTPUT" : "RETAINED_SOURCE",
                "passed", preview.passed(), "expectedMatch", preview.expectedMatch(), "sourceDocument", document(source),
                "findings", findings, "findingsTruncated", preview.warnings().size() + preview.findings().size() > FINDING_LIMIT);
    }

    Map<String, Object> readiness(Map<String, Object> args) {
        var source = documentArgs(args);
        var report = exchanges.checkReadiness(id(args, "agreementId"), id(args, "capabilityId"),
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
        return fields("exchangeId", exchange.id(), "direction", exchange.direction(), "partnerId", exchange.partnerId(),
                "agreementId", exchange.agreementId(), "agreementRevision", exchange.agreementRevision(),
                "capabilityId", exchange.capabilityId(), "status", exchange.status(),
                "messageOrdinal", exchange.messageOrdinal(), "transferId", exchange.mftTransferId(),
                "createdAt", exchange.createdAt(), "updatedAt", exchange.updatedAt(),
                "workspacePath", "/documents");
    }

    private static Map<String, Object> document(DocumentReference reference) {
        return reference == null ? null : fields("documentSetId", reference.documentSetId(), "documentId",
                reference.documentId(), "representationId", reference.representationId(), "contentHash", reference.contentHash());
    }

    private static DocumentReference documentArgs(Map<String, Object> args) {
        return new DocumentReference(id(args, "documentSetId"), id(args, "documentId"), id(args, "representationId"),
                text(args, "contentHash"));
    }

    private static Map<String, Object> finding(String code, String path) {
        return fields("code", safeCode(code), "path", safePath(path));
    }

    private static String safeCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,79}") ? value : "UNCLASSIFIED";
    }

    private static String safePath(String value) {
        return value != null && value.matches("[A-Za-z0-9_.$/\\[\\]:-]{1,160}") ? value : "/";
    }

    static Map<String, Object> fields(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            Object value = entries[index + 1];
            if (value instanceof UUID || value instanceof Instant || value instanceof Enum<?>) value = value.toString();
            if (value != null) result.put((String) entries[index], value);
        }
        return result;
    }

    private static UUID id(Map<String, Object> args, String key) {
        return UUID.fromString(text(args, key));
    }

    private static String text(Map<String, Object> args, String key) {
        if (args.get(key) instanceof String value && !value.isBlank()) return value;
        throw new IllegalArgumentException();
    }

    private static byte[] bytes(Map<String, Object> args, String key) {
        byte[] bytes = text(args, key).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32_768) throw new IllegalArgumentException();
        return bytes;
    }

    private static <T> List<T> tail(List<T> values) {
        return values.subList(Math.max(0, values.size() - FINDING_LIMIT), values.size());
    }

    static final class EvidenceMissing extends RuntimeException {}
}
