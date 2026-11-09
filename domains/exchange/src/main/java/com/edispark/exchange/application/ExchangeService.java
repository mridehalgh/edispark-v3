package com.edispark.exchange.application;

import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeRepository;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.OutboundLossPolicy;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import io.cottn.core.tenancy.TenantContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.edispark.exchange.domain.ExchangeWarning;
import org.springframework.stereotype.Service;

@Service
public final class ExchangeService {
    private final ExchangeRepository exchanges;
    private final PartnershipRepository partnerships;
    private final DocumentAcl documents;
    private final TransformationAcl transformations;
    private final TransportAcl transport;
    private final Clock clock;

    public ExchangeService(ExchangeRepository exchanges, PartnershipRepository partnerships, DocumentAcl documents,
                           TransformationAcl transformations, TransportAcl transport, Clock clock) {
        this.exchanges = exchanges; this.partnerships = partnerships; this.documents = documents;
        this.transformations = transformations; this.transport = transport; this.clock = clock;
    }

    public List<EligibleRoute> eligibleRoutes(UUID documentSetId, UUID documentId) {
        String tenant = tenant();
        var document = documents.describe(documentSetId, documentId);
        return partnerships.listAgreements(tenant).stream()
                .filter(item -> item.status() == AgreementStatus.ACTIVE)
                .filter(item -> partnerships.findPartner(tenant, item.partnerId()).filter(partner -> partner.status() == com.edispark.partnerships.domain.model.PartnerStatus.ACTIVE).isPresent())
                .filter(item -> document.partnerId() == null || item.partnerId().equals(document.partnerId()))
                .flatMap(agreement -> agreement.capabilities().stream()
                        .filter(capability -> capability.direction() == ExchangeDirection.OUTBOUND)
                        .filter(capability -> capability.documentType() == document.type())
                        .filter(capability -> document.partnerId() != null || document.connectionId() == null || capability.as2ConnectionId().equals(document.connectionId()))
                        .map(capability -> new EligibleRoute(agreement.id(), agreement.revision(), agreement.partnerId(),
                                agreement.name(), capability)))
                .toList();
    }

    public EdiExchange submitOutbound(SubmitOutbound command) {
        String tenant = tenant();
        EdiExchange prior = exchanges.findByIdempotencyKey(tenant, command.idempotencyKey()).orElse(null);
        if (prior != null) return prior;
        TradingAgreement agreement = partnerships.findAgreement(tenant, command.agreementId())
                .filter(item -> item.status() == AgreementStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active agreement was not found"));
        ExchangeCapability capability = agreement.capabilities().stream()
                .filter(item -> item.id().equals(command.capabilityId()))
                .filter(item -> item.direction() == ExchangeDirection.OUTBOUND)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Outbound capability was not found"));
        var descriptor = documents.describe(command.documentSetId(), command.documentId());
        if (descriptor.type() != capability.documentType()) throw new IllegalArgumentException("Document type does not match capability");
        EdiExchange exchange = EdiExchange.outbound(tenant, agreement.partnerId(), agreement.id(), agreement.revision(),
                capability.id(), command.idempotencyKey(), descriptor.current(), clock.instant());
        EdiExchange claimed = exchanges.save(exchange);
        if (!claimed.id().equals(exchange.id())) return claimed;
        return process(exchange, capability, descriptor);
    }

    private EdiExchange process(EdiExchange exchange, ExchangeCapability capability, DocumentAcl.DocumentDescriptor descriptor) {
        try {
            exchange.advance(ExchangeStatus.RESOLVING, "Active agreement revision resolved", clock.instant());
            exchange.advance(ExchangeStatus.VALIDATING, "Checking document, partner requirements and mapping coverage before delivery", clock.instant());
            Prepared checked = prepare(exchange.agreementId(), capability, descriptor);
            if (!checked.report().ready()) {
                var evidenceJson = new ObjectMapper().valueToTree(java.util.Map.of(
                        "ready", false, "checkedAt", checked.report().checkedAt().toString(),
                        "sourceDocument", descriptor.current(), "agreementId", exchange.agreementId(),
                        "agreementRevision", exchange.agreementRevision(), "mappingRevisionId", capability.mappingRevisionId(),
                        "issues", checked.report().issues()));
                byte[] evidence = new ObjectMapper().writeValueAsBytes(evidenceJson);
                var report = documents.retainDerivative(descriptor.current(), DocumentAcl.GeneratedArtifact.VALIDATION_REPORT,
                        evidence, capability.mappingRevisionId(), exchange.id());
                exchange.mappingEvidence(report, checked.report().issues().stream().map(issue -> new ExchangeWarning(
                        issue.code(), issue.path(), issue.owner(), issue.message())).toList());
                exchange.advance(ExchangeStatus.NEEDS_REVIEW, "Partner readiness failed. Nothing was submitted to transport.", clock.instant());
                return exchanges.save(exchange);
            }
            exchange.advance(ExchangeStatus.TRANSFORMING, "Preparing delivery from the validated mapping output", clock.instant());
            var transformation = checked.transformation();
            exchange.advance(ExchangeStatus.STORING, "Retaining generated representation", clock.instant());
            DocumentReference derivative = documents.retainDerivative(descriptor.current(),
                    DocumentAcl.GeneratedArtifact.EDI_PAYLOAD,
                    transformation.payload(), capability.mappingRevisionId(), exchange.id());
            exchange.generated(derivative);
            DocumentReference report = documents.retainDerivative(descriptor.current(),
                    DocumentAcl.GeneratedArtifact.MAPPING_REPORT,
                    transformation.report(), capability.mappingRevisionId(), exchange.id());
            exchange.mappingEvidence(report, transformation.warnings());
            if (!transformation.warnings().isEmpty()
                    && capability.outboundLossPolicy() == OutboundLossPolicy.STRICT) {
                exchange.advance(ExchangeStatus.NEEDS_REVIEW,
                        "Mapping stopped before transport because populated source fields were not represented",
                        clock.instant());
                return exchanges.save(exchange);
            }
            if (!transformation.warnings().isEmpty()) {
                exchange.advance(ExchangeStatus.QUEUING_TRANSPORT,
                        "Mapping warnings accepted by the active agreement", clock.instant());
            } else {
                exchange.advance(ExchangeStatus.QUEUING_TRANSPORT, "Submitting to AS2 connection", clock.instant());
            }
            documents.content(descriptor.current()); // Recheck the exact source identity immediately before dispatch.
            var active = partnerships.findAgreement(tenant(), exchange.agreementId()).orElseThrow();
            if (active.status() != AgreementStatus.ACTIVE || active.revision() != exchange.agreementRevision()) {
                throw new IllegalArgumentException("The partner route changed. Check readiness again before sending.");
            }
            UUID transfer = transport.submit(capability.as2ConnectionId(), exchange.idempotencyKey(),
                    filename(descriptor.businessReference(), capability), contentType(capability), transformation.payload(), exchange.id(),
                    exchange.agreementId(), exchange.agreementRevision(), exchange.capabilityId(),
                    exchange.sourceDocument(), capability.mappingRevisionId());
            exchange.queued(transfer, clock.instant());
            return exchanges.save(exchange);
        } catch (IllegalArgumentException exception) {
            exchange.advance(ExchangeStatus.NEEDS_REVIEW, exception.getMessage(), clock.instant());
            return exchanges.save(exchange);
        } catch (Exception exception) {
            exchange.advance(ExchangeStatus.FAILED, "A transient exchange dependency failed", clock.instant());
            return exchanges.save(exchange);
        }
    }

    /** No files, exchanges or transfers are created by this dry run. Send always repeats it. */
    public ReadinessReport checkReadiness(UUID agreementId, UUID capabilityId, UUID setId, UUID documentId) {
        var agreement = partnerships.findAgreement(tenant(), agreementId)
                .filter(item -> item.status() == AgreementStatus.ACTIVE).orElseThrow(() -> new IllegalArgumentException("Select an active partner agreement."));
        var capability = agreement.capabilities().stream().filter(item -> item.id().equals(capabilityId)
                && item.direction() == ExchangeDirection.OUTBOUND).findFirst().orElseThrow(() -> new IllegalArgumentException("Select an outbound route."));
        return prepare(agreementId, capability, documents.describe(setId, documentId)).report();
    }

    private Prepared prepare(UUID agreementId, ExchangeCapability capability, DocumentAcl.DocumentDescriptor descriptor) {
        var agreement = partnerships.findAgreement(tenant(), agreementId).orElseThrow();
        List<ReadinessIssue> issues = new ArrayList<>();
        if (partnerships.findPartner(tenant(), agreement.partnerId()).filter(partner -> partner.status() == com.edispark.partnerships.domain.model.PartnerStatus.ACTIVE).isEmpty()) {
            issues.add(ReadinessIssue.configuration("PARTNER_UNAVAILABLE", "The trading partner is unavailable or suspended."));
        }
        if (!"FINAL".equals(descriptor.lifecycleStatus())) issues.add(ReadinessIssue.document("DOCUMENT_NOT_FINAL", "/", "Validate and finalize this draft before sending. Cancelled documents cannot be sent."));
        if (descriptor.type() != capability.documentType()) issues.add(ReadinessIssue.configuration("DOCUMENT_TYPE_MISMATCH", "The selected route is for another document type."));
        if (descriptor.partnerId() != null && !descriptor.partnerId().equals(agreement.partnerId())
                || descriptor.partnerId() == null && descriptor.connectionId() != null && !descriptor.connectionId().equals(capability.as2ConnectionId())) {
            issues.add(ReadinessIssue.configuration("PARTNER_MISMATCH", "The selected route does not belong to this document's retained trading partner."));
        }
        issues.addAll(documents.validate(descriptor));
        byte[] canonical = documents.content(descriptor.current());
        if (capability.documentProfileRevisionId() != null) {
            var profile = partnerships.findDocumentProfile(tenant(), capability.documentProfileRevisionId()).orElse(null);
            if (profile == null || profile.status() != com.edispark.partnerships.domain.model.DocumentProfileStatus.PUBLISHED
                    || !profile.partnerId().equals(agreement.partnerId()) || profile.documentType() != descriptor.type()
                    || profile.direction() != ExchangeDirection.OUTBOUND) issues.add(ReadinessIssue.configuration("PROFILE_UNAVAILABLE", "The pinned partner profile is unavailable or does not match this route."));
            else issues.addAll(PartnerProfileValidation.validate(profile, canonical));
        }
        TransformationAcl.Result transformed = null;
        if (issues.isEmpty()) {
            try {
                transformed = transformations.executePublished(capability, canonical);
                for (var warning : transformed.warnings()) issues.add(new ReadinessIssue(warning.code(), warning.sourcePath(), warning.message(),
                        "MAPPING", capability.outboundLossPolicy() == OutboundLossPolicy.STRICT ? "ERROR" : "WARNING",
                        "Ask a mapping administrator to cover this field or document a reviewed omission in a new mapping revision."));
            } catch (IllegalArgumentException exception) {
                issues.add(new ReadinessIssue("MAPPING_VALIDATION_FAILED", "/", exception.getMessage(), "MAPPING", "ERROR", "Review the selected mapping and its target EDI requirements."));
            } catch (RuntimeException exception) { issues.add(ReadinessIssue.configuration("VALIDATION_UNAVAILABLE", "Partner readiness could not be completed. Nothing has been approved for delivery.")); }
        }
        return new Prepared(new ReadinessReport(issues.stream().noneMatch(issue -> issue.severity().equals("ERROR")), clock.instant(), descriptor.current(),
                agreementId, agreement.revision(), capability.id(), capability.mappingRevisionId(), capability.documentProfileRevisionId(), List.copyOf(issues)), transformed);
    }

    public record ReadinessReport(boolean ready, Instant checkedAt, DocumentReference sourceDocument, UUID agreementId,
            int agreementRevision, UUID capabilityId, UUID mappingRevisionId, UUID documentProfileRevisionId, List<ReadinessIssue> issues) {}
    private record Prepared(ReadinessReport report, TransformationAcl.Result transformation) {}

    public List<EdiExchange> list() { return exchanges.list(tenant()); }
    public EdiExchange get(UUID id) { return exchanges.find(tenant(), id).orElseThrow(() -> new IllegalArgumentException("Exchange was not found")); }

    private String filename(String reference, ExchangeCapability capability) {
        String safe = reference == null ? "document" : reference.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe + "-" + capability.messageType().toLowerCase(java.util.Locale.ROOT) + ".edi";
    }
    private String contentType(ExchangeCapability capability) { return capability.standard().name().equals("EDIFACT") ? "application/edifact" : "application/edi"; }
    private String tenant() { return TenantContext.getCurrentTenant().asString(); }

    public record SubmitOutbound(UUID agreementId, UUID capabilityId, UUID documentSetId, UUID documentId,
                                 String idempotencyKey) { }
    public record EligibleRoute(UUID agreementId, int agreementRevision, UUID partnerId, String agreementName,
                                ExchangeCapability capability) { }
}
