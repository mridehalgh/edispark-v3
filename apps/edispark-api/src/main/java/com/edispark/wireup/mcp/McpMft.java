package com.edispark.wireup.mcp;

import static com.edispark.wireup.mcp.McpResponseFields.fields;

import com.edispark.mft.application.MftService;
import com.edispark.mft.application.ConnectionDetailsService;
import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferDirection;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.application.MftEvidenceQuery;
import com.edispark.mft.application.MftEvidenceQuery.ConnectionField;
import com.edispark.mft.application.MftEvidenceQuery.DateWindow;
import com.edispark.mft.application.MftEvidenceQuery.Page;
import com.edispark.mft.application.MftEvidenceQuery.ReceiptDisposition;
import com.edispark.mft.application.MftEvidenceQuery.TransferSearch;
import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.MdnDeliveryStatus;
import com.edispark.mft.domain.model.PartnerStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "edispark.mcp.enabled", havingValue = "true")
final class McpMft {
    private static final int PAGE_SIZE = 32;
    private final MftEvidenceQuery evidence;
    private final MftService service;
    private final ConnectionDetailsService connectionDetails;

    McpMft(MftEvidenceQuery evidence, MftService service, ConnectionDetailsService connectionDetails) {
        this.evidence = evidence;
        this.service = service;
        this.connectionDetails = connectionDetails;
    }

    Map<String, Object> transfers(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var transferId = args.optionalId("transferId");
        if (transferId != null) {
            requireOnly(values, "transferId");
            return fields("matches", List.of(transferSummary(evidence.transfer(new TransferId(transferId)))));
        }
        var search = new TransferSearch(new DateWindow(args.optionalInstant("from"), args.optionalInstant("to")),
                args.optionalId("connectionId"), args.optionalEnum("direction", TransferDirection.class),
                args.optionalEnum("status", TransferStatus.class));
        return page("find_transfers", args, cursor, tenant, after -> evidence.findTransfers(search, after), McpMft::transferSummary);
    }

    Map<String, Object> trace(Map<String, Object> args) {
        var transfer = transfer(args);
        var attempts = transfer.attempts();
        var provenance = transfer.provenance();
        var disposition = evidence.receiptDisposition(transfer);
        var result = transferSummary(transfer);
        result.putAll(fields("nextAttemptAt", transfer.status() == TransferStatus.RETRY_SCHEDULED ? transfer.nextAttemptAt() : null,
                "attempts", attempts.stream().skip(Math.max(0, attempts.size() - PAGE_SIZE)).map(attempt ->
                        fields("number", attempt.number(), "startedAt", attempt.startedAt(), "completedAt", attempt.completedAt(),
                                "httpStatus", attempt.httpStatus(), "outcome", McpResponseFields.safeCode(attempt.outcome()))).toList(),
                "attemptsTruncated", attempts.size() > PAGE_SIZE, "resendOf", transfer.resendOf() == null ? null : transfer.resendOf().value(),
                "transportReceipt", disposition == ReceiptDisposition.NOT_RECORDED ? "NOT_RECORDED" : "RECORDED",
                "receiptDisposition", disposition == ReceiptDisposition.NOT_RECORDED ? null : disposition,
                "provenance", provenance == null ? null : fields("exchangeId", provenance.exchangeId(),
                        "documentSetId", provenance.documentSetId(), "documentId", provenance.documentId(),
                        "agreementId", provenance.agreementId(), "agreementRevision", provenance.agreementRevision(),
                        "capabilityId", provenance.capabilityId(), "mappingRevisionId", provenance.mappingRevisionId())));
        return result;
    }

    Map<String, Object> receipts(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var transferId = new TransferId(args.id("transferId"));
        return page("find_mdn_deliveries", args, cursor, tenant, after -> evidence.receiptDeliveries(transferId, after),
                item -> fields("deliveryId", item.id(), "status", item.status(), "attempts", item.attempts(),
                        "nextAttemptAt", item.status() == MdnDeliveryStatus.PENDING ? item.nextAttemptAt() : null, "updatedAt", item.updatedAt()));
    }

    Map<String, Object> connections(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var id = args.optionalId("connectionId");
        if (id != null) {
            requireOnly(values, "connectionId");
            return fields("matches", List.of(connection(evidence.connection(id))));
        }
        var status = args.optionalEnum("status", PartnerStatus.class);
        return page("find_connections", args, cursor, tenant, after -> evidence.findConnections(status, after), McpMft::connection);
    }

    Map<String, Object> certificates(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var id = args.optionalId("certificateId");
        if (id != null) {
            requireOnly(values, "certificateId");
            return fields("matches", List.of(certificate(evidence.certificate(id))));
        }
        var from = args.optionalInstant("from");
        var to = args.optionalInstant("to");
        var expiry = from == null && to == null ? null : new DateWindow(from, to);
        var kind = args.optionalEnum("kind", CertificateKind.class);
        return page("find_certificates", args, cursor, tenant, after -> evidence.findCertificates(kind, expiry, after), McpMft::certificate);
    }

    Map<String, Object> setup(Map<String, Object> args) {
        var setup = connectionDetails.get(id(args, "connectionId"));
        return fields("connectionId", setup.partnerId(), "as2Identifier", McpResponseFields.safePath(setup.as2Identifier()),
                "encryptionAlgorithms", setup.supportedEncryptionAlgorithms(), "signatureAlgorithms", setup.supportedSignatureAlgorithms(),
                "keyEncryptionAlgorithm", setup.keyEncryptionAlgorithm(), "certificateId", setup.certificateId(),
                "certificateFingerprint", setup.certificateFingerprintSha256(), "certificateExpiresAt", setup.certificateNotAfter(),
                "encryptionCertificateFingerprint", setup.encryptionCertificateFingerprintSha256(),
                "encryptionCertificateExpiresAt", setup.encryptionCertificateNotAfter(),
                "workspacePath", "/connections/" + setup.partnerId());
    }

    Map<String, Object> certificateUsage(Map<String, Object> values, McpCursor cursor, String tenant) {
        var args = new McpArguments(values);
        var id = args.id("certificateId");
        return page("get_certificate_usage", args, cursor, tenant, after -> evidence.certificateUsage(id, after), item ->
                fields("connectionId", item.connectionId(), "revision", item.revision(), "status", item.status(),
                        "localIdentity", item.localIdentity(), "partnerSigning", item.partnerSigning(),
                        "partnerEncryption", item.partnerEncryption(), "previousPartnerSigning", item.previousPartnerSigning()));
    }

    Map<String, Object> compareConnections(Map<String, Object> values) {
        var args = new McpArguments(values);
        var baseline = args.id("baselineConnectionId");
        var candidate = args.id("candidateConnectionId");
        var changes = evidence.compareConnections(baseline, candidate).stream().map(McpMft::wireField).toList();
        return fields("candidateConnectionId", candidate, "baselineConnectionId", baseline, "changedFields", changes);
    }

    Map<String, Object> readiness(Map<String, Object> args) {
        var findings = service.checkConnectionReadiness(id(args, "connectionId"));
        return fields("ready", findings.isEmpty(), "findings", findings);
    }

    Map<String, Object> rollover(Map<String, Object> args) {
        var findings = service.previewCertificateRollover(id(args, "connectionId"), id(args, "signingCertificateId"),
                id(args, "encryptionCertificateId"));
        return fields("ready", findings.isEmpty(), "findings", findings);
    }

    private static <T> Map<String, Object> page(String tool, McpArguments args, McpCursor cursor, String tenant,
            Function<UUID, Page<T>> query, Function<T, Map<String, Object>> project) {
        String binding = args.cursorBinding(tool);
        var result = query.apply(args.uuidPosition(cursor, tenant, binding));
        return fields("matches", result.items().stream().map(project).toList(),
                "nextCursor", result.next() == null ? null : cursor.encode(result.next(), tenant, binding));
    }

    private Transfer transfer(Map<String, Object> args) {
        return evidence.transfer(new TransferId(new McpArguments(args).id("transferId")));
    }

    private static Map<String, Object> transferSummary(Transfer transfer) {
        return fields("transferId", transfer.id().value(), "connectionId", transfer.partnerId(), "status", transfer.status(),
                "direction", transfer.direction(), "createdAt", transfer.createdAt(), "updatedAt", transfer.updatedAt(),
                "workspacePath", "/files/transfers/" + transfer.id().value());
    }

    private static Map<String, Object> connection(PartnerProfile item) {
        return fields("connectionId", item.id(), "familyId", item.familyId(), "revision", item.revision(), "status", item.status(),
                "scheduledAt", item.scheduledAt(), "updatedAt", item.updatedAt(), "mdnMode", item.mdnMode(),
                "signingRequired", item.signingRequired(), "encryptionRequired", item.encryptionRequired(),
                "compressionEnabled", item.compressionEnabled(), "legacyCryptoEnabled", item.legacyCryptoEnabled(),
                "localCertificateId", item.localCertificateId(), "partnerSigningCertificateId", item.partnerSigningCertificateId(),
                "partnerEncryptionCertificateId", item.partnerEncryptionCertificateId(),
                "signingCertificateGraceUntil", item.signingCertificateGraceUntil(), "workspacePath", "/connections/" + item.id());
    }

    private static Map<String, Object> certificate(CertificateRecord item) {
        return fields("certificateId", item.id(), "kind", item.kind(), "fingerprintSha256", item.fingerprintSha256(),
                "notBefore", item.notBefore(), "notAfter", item.notAfter(), "workspacePath", "/connections/certificates");
    }

    private static String wireField(ConnectionField field) {
        return switch (field) {
            case RECEIPT_MODE -> "mdnMode";
            case SIGNING_POLICY -> "signingRequired";
            case ENCRYPTION_POLICY -> "encryptionRequired";
            case COMPRESSION -> "compressionEnabled";
            case LEGACY_CRYPTOGRAPHY -> "legacyCryptoEnabled";
            case LOCAL_CERTIFICATE -> "localCertificateId";
            case SIGNING_CERTIFICATE -> "partnerSigningCertificateId";
            case ENCRYPTION_CERTIFICATE -> "partnerEncryptionCertificateId";
            case SIGNING_GRACE_PERIOD -> "signingCertificateGraceUntil";
            case DELIVERY_ENDPOINT -> "endpoint";
            case RECEIPT_ENDPOINT -> "asynchronousMdnEndpoint";
        };
    }

    private static UUID id(Map<String, Object> values, String key) {
        return new McpArguments(values).id(key);
    }

    private static void requireOnly(Map<String, Object> args, String key) {
        if (!args.keySet().equals(java.util.Set.of(key))) throw new IllegalArgumentException("Use the ID without search filters");
    }
}
