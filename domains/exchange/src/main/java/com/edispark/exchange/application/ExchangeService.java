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
        return eligibleRoutes(documentSetId, documentId, false);
    }

    public List<EligibleRoute> previewEligibleRoutes(UUID documentSetId, UUID documentId) {
        return eligibleRoutes(documentSetId, documentId, true);
    }

    private List<EligibleRoute> eligibleRoutes(UUID documentSetId, UUID documentId, boolean preview) {
        var document = documents.describe(documentSetId, documentId);
        return partnerships.listAgreements().stream()
                .filter(item -> item.status() == AgreementStatus.ACTIVE)
                .filter(item -> partnerships.findPartner(item.partnerId()).filter(partner -> partner.status() == com.edispark.partnerships.domain.model.PartnerStatus.ACTIVE).isPresent())
                .filter(item -> document.partnerId() == null || item.partnerId().equals(document.partnerId()))
                .flatMap(agreement -> agreement.capabilities().stream()
                        .filter(capability -> capability.direction() == ExchangeDirection.OUTBOUND)
                        .filter(capability -> capability.documentType() == document.type())
                        .filter(capability -> document.partnerId() != null || document.connectionId() == null || capability.as2ConnectionId().equals(document.connectionId()))
                        .filter(capability -> currentTargetQualification(agreement, preview
                                ? transformations.previewQualification(capability) : transformations.qualification(capability)))
                        .map(capability -> new EligibleRoute(agreement.id(), agreement.revision(), agreement.partnerId(),
                                agreement.name(), capability)))
                .toList();
    }

    public EdiExchange submitOutbound(SubmitOutbound command) {
        EdiExchange prior = exchanges.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (prior != null) return prior;
        TradingAgreement agreement = partnerships.findAgreement(command.agreementId())
                .filter(item -> item.status() == AgreementStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active agreement was not found"));
        ExchangeCapability capability = agreement.capabilities().stream()
                .filter(item -> item.id().equals(command.capabilityId()))
                .filter(item -> item.direction() == ExchangeDirection.OUTBOUND)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Outbound capability was not found"));
        var descriptor = documents.describe(command.documentSetId(), command.documentId());
        if (descriptor.type() != capability.documentType()) throw new IllegalArgumentException("Document type does not match capability");
        EdiExchange exchange = exchanges.outbound( agreement.partnerId(), agreement.id(), agreement.revision(),
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
            var transformation = transformations.prepareForDispatch(capability, documents.content(descriptor.current()), exchange.id(), checked.transformation());
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
            var active = partnerships.findAgreement(exchange.agreementId()).orElseThrow();
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
        return checkReadiness(agreementId, capabilityId, setId, documentId, null);
    }

    public ReadinessReport checkReadiness(UUID agreementId, UUID capabilityId, UUID setId, UUID documentId,
                                          DocumentReference expected) {
        var agreement = partnerships.findAgreement(agreementId)
                .filter(item -> item.status() == AgreementStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Select an active partner agreement."));
        var capability = agreement.capabilities().stream().filter(item -> item.id().equals(capabilityId)
                && item.direction() == ExchangeDirection.OUTBOUND).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select an outbound route."));
        var descriptor = documents.describe(setId, documentId);
        if (expected != null && !expected.equals(descriptor.current())) throw new SourceChanged();
        return prepare(agreement, capability, descriptor, expected != null).report();
    }

    public static final class SourceChanged extends IllegalArgumentException {}

    private Prepared prepare(UUID agreementId, ExchangeCapability capability, DocumentAcl.DocumentDescriptor descriptor) {
        return prepare(partnerships.findAgreement(agreementId).orElseThrow(), capability, descriptor, false);
    }

    private Prepared prepare(TradingAgreement agreement, ExchangeCapability capability,
                             DocumentAcl.DocumentDescriptor descriptor, boolean preview) {
        List<ReadinessIssue> issues = new ArrayList<>();
        if (partnerships.findPartner(agreement.partnerId()).filter(partner -> partner.status() == com.edispark.partnerships.domain.model.PartnerStatus.ACTIVE).isEmpty()) {
            issues.add(ReadinessIssue.configuration("PARTNER_UNAVAILABLE", "The trading partner is unavailable or suspended."));
        }
        if (!"FINAL".equals(descriptor.lifecycleStatus())) issues.add(ReadinessIssue.document("DOCUMENT_NOT_FINAL", "/", "Validate and finalize this draft before sending. Cancelled documents cannot be sent."));
        if (descriptor.type() != capability.documentType()) issues.add(ReadinessIssue.configuration("DOCUMENT_TYPE_MISMATCH", "The selected route is for another document type."));
        if (descriptor.partnerId() != null && !descriptor.partnerId().equals(agreement.partnerId())
                || descriptor.partnerId() == null && descriptor.connectionId() != null && !descriptor.connectionId().equals(capability.as2ConnectionId())) {
            issues.add(ReadinessIssue.configuration("PARTNER_MISMATCH", "The selected route does not belong to this document's retained trading partner."));
        }
        issues.addAll(documents.validate(descriptor));
        var qualification = preview ? transformations.previewQualification(capability) : transformations.qualification(capability);
        if (!currentTargetQualification(agreement, qualification)) issues.add(ReadinessIssue.configuration("TARGET_QUALIFICATION_STALE",
                "The selected route has no current target validation proof. Ask an administrator to publish a tested mapping and requalify the agreement."));
        byte[] canonical = documents.content(descriptor.current());
        if (capability.documentProfileRevisionId() != null) {
            var profile = partnerships.findDocumentProfile(capability.documentProfileRevisionId()).orElse(null);
            if (profile == null || profile.status() != com.edispark.partnerships.domain.model.DocumentProfileStatus.PUBLISHED
                    || !profile.partnerId().equals(agreement.partnerId()) || profile.documentType() != descriptor.type()
                    || profile.direction() != ExchangeDirection.OUTBOUND) issues.add(ReadinessIssue.configuration("PROFILE_UNAVAILABLE", "The pinned partner profile is unavailable or does not match this route."));
            else issues.addAll(PartnerProfileValidation.validate(profile, canonical));
        }
        TransformationAcl.Result transformed = null;
        if (issues.isEmpty()) {
            try {
                transformed = preview ? transformations.previewPublished(capability, canonical)
                        : transformations.executePublished(capability, canonical);
                for (var warning : transformed.warnings()) issues.add(new ReadinessIssue(warning.code(), warning.sourcePath(), warning.message(),
                        "MAPPING", capability.outboundLossPolicy() == OutboundLossPolicy.STRICT ? "ERROR" : "WARNING",
                        "Ask a mapping administrator to cover this field or document a reviewed omission in a new mapping revision."));
            } catch (ReadinessFailure exception) {
                issues.addAll(exception.issues());
            } catch (IllegalArgumentException exception) {
                issues.add(new ReadinessIssue("MAPPING_VALIDATION_FAILED", "/", exception.getMessage(), "MAPPING", "ERROR", "Review the selected mapping and its target EDI requirements."));
            } catch (RuntimeException exception) { issues.add(ReadinessIssue.configuration("VALIDATION_UNAVAILABLE", "Partner readiness could not be completed. Nothing has been approved for delivery.")); }
        }
        return new Prepared(new ReadinessReport(issues.stream().noneMatch(issue -> issue.severity().equals("ERROR")), clock.instant(), descriptor.current(),
                agreement.id(), agreement.revision(), capability.id(), capability.mappingRevisionId(), capability.documentProfileRevisionId(), List.copyOf(issues)), transformed);
    }

    public record ReadinessReport(boolean ready, Instant checkedAt, DocumentReference sourceDocument, UUID agreementId,
            int agreementRevision, UUID capabilityId, UUID mappingRevisionId, UUID documentProfileRevisionId, List<ReadinessIssue> issues) {}
    private record Prepared(ReadinessReport report, TransformationAcl.Result transformation) {}

    private boolean currentTargetQualification(TradingAgreement agreement, TransformationAcl.Qualification evidence) {
        return evidence.valid() && (evidence.identity().isEmpty() || agreement.qualification() != null
                && agreement.qualification().passed() && agreement.qualification().checks().contains(evidence.identity()));
    }

    public List<EdiExchange> list() { return exchanges.list().stream().map(this::refreshReceipt).toList(); }
    public EdiExchange get(UUID id) { return refreshReceipt(exchanges.find(id).orElseThrow(() -> new IllegalArgumentException("Exchange was not found"))); }

    /** Reconciles a durable MFT fact, so a missed notification cannot strand the read projection. */
    private EdiExchange refreshReceipt(EdiExchange exchange) {
        if (exchange.direction() != ExchangeDirection.OUTBOUND || exchange.status() != ExchangeStatus.AWAITING_RECEIPT
                || exchange.mftTransferId() == null) return exchange;
        try {
            var receipt = transport.receipt(exchange.mftTransferId(), exchange.id());
            if (receipt.outcome() == TransportAcl.Outcome.PENDING) return exchange;
            var next = ExchangeStatus.valueOf(receipt.outcome().name());
            String detail = receipt.detail();
            if (next == ExchangeStatus.DELIVERED && requiresFunctionalAcknowledgement(exchange)) {
                next = ExchangeStatus.AWAITING_RECEIPT;
                detail = "AS2 transport receipt verified; awaiting functional acknowledgement. Business acceptance is not yet recorded.";
            }
            if (exchange.stages().getLast().detail().equals(detail)) return exchange;
            var at = receipt.recordedAt() == null || receipt.recordedAt().isBefore(exchange.updatedAt())
                    ? exchange.updatedAt() : receipt.recordedAt();
            exchange.advance(next, detail, at);
            return exchanges.save(exchange);
        } catch (RuntimeException exception) {
            // Keep the persisted pending state; an unavailable receipt must not approve or poison another exchange.
            return exchange;
        }
    }

    private boolean requiresFunctionalAcknowledgement(EdiExchange exchange) {
        return partnerships.findAgreement(exchange.agreementId()).stream()
                .flatMap(agreement -> agreement.capabilities().stream()).filter(cap -> cap.id().equals(exchange.capabilityId()))
                .map(cap -> cap.acknowledgementPolicy() == com.edispark.partnerships.domain.model.AcknowledgementPolicy.FUNCTIONAL)
                .findFirst().orElse(true);
    }

    private String filename(String reference, ExchangeCapability capability) {
        String safe = reference == null ? "document" : reference.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe + "-" + capability.messageType().toLowerCase(java.util.Locale.ROOT) + ".edi";
    }
    private String contentType(ExchangeCapability capability) { return capability.standard().name().equals("EDIFACT") ? "application/edifact" : "application/edi"; }

    public record SubmitOutbound(UUID agreementId, UUID capabilityId, UUID documentSetId, UUID documentId,
                                 String idempotencyKey) { }
    public record EligibleRoute(UUID agreementId, int agreementRevision, UUID partnerId, String agreementName,
                                ExchangeCapability capability) { }
}
