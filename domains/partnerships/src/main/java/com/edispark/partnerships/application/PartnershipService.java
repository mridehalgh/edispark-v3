package com.edispark.partnerships.application;

import com.edispark.partnerships.domain.model.BusinessIdentity;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import com.edispark.partnerships.domain.model.PartnerStatus;
import com.edispark.partnerships.domain.model.QualificationReport;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import io.cottn.core.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class PartnershipService {
    private final PartnershipRepository repository;
    private final QualificationAcl qualification;
    private final AgreementSnapshotPublisher snapshots;
    private final Clock clock;

    public PartnershipService(PartnershipRepository repository, QualificationAcl qualification,
                              AgreementSnapshotPublisher snapshots, Clock clock) {
        this.repository = repository; this.qualification = qualification; this.snapshots = snapshots; this.clock = clock;
    }

    public TradingPartner createPartner(String name, List<BusinessIdentity> identities,
            InboundFileFailurePolicy failurePolicy) {
        String tenant = tenant();
        for (BusinessIdentity identity : identities) {
            boolean duplicate = repository.listPartners(tenant).stream()
                    .flatMap(item -> item.identities().stream()).anyMatch(identity::equals);
            if (duplicate) throw new IllegalArgumentException("Business identity is already registered");
        }
        return repository.savePartner(TradingPartner.create(tenant, name, identities,
                failurePolicy == null ? InboundFileFailurePolicy.REJECT_FILE : failurePolicy, clock.instant()));
    }

    public TradingPartner createPartner(String name, List<BusinessIdentity> identities) {
        return createPartner(name, identities, InboundFileFailurePolicy.REJECT_FILE);
    }

    public List<TradingPartner> listPartners() { promoteDue(); return repository.listPartners(tenant()); }

    public TradingPartner getPartner(UUID id) { return requirePartner(id); }

    public TradingPartner createPartnerRevision(UUID id) {
        return repository.savePartner(requirePartner(id).newDraft(clock.instant()));
    }

    public TradingPartner updatePartner(UUID id, String name, List<BusinessIdentity> identities,
            InboundFileFailurePolicy failurePolicy) {
        ensureUniqueIdentities(id, identities);
        TradingPartner current = requirePartner(id);
        InboundFileFailurePolicy effectivePolicy = failurePolicy == null
                ? current.inboundFileFailurePolicy() : failurePolicy;
        return repository.savePartner(current.revise(name, identities, effectivePolicy, clock.instant()));
    }

    public TradingPartner updatePartner(UUID id, String name, List<BusinessIdentity> identities) {
        return updatePartner(id, name, identities, requirePartner(id).inboundFileFailurePolicy());
    }

    public TradingPartner activatePartner(UUID id, Instant effectiveAt) {
        TradingPartner candidate = requirePartner(id);
        Instant now = clock.instant();
        if (effectiveAt != null && effectiveAt.isAfter(now)) {
            return repository.savePartner(candidate.schedule(effectiveAt, now));
        }
        repository.listPartners(tenant()).stream()
                .filter(item -> item.familyId().equals(candidate.familyId()) && !item.id().equals(candidate.id()))
                .map(item -> item.supersede(now)).filter(item -> item.status() == PartnerStatus.SUPERSEDED)
                .forEach(repository::savePartner);
        return repository.savePartner(candidate.activate(now));
    }

    public TradingPartner archivePartner(UUID id) {
        return repository.savePartner(requirePartner(id).archive(clock.instant()));
    }

    public void deletePartnerDraft(UUID id) {
        TradingPartner partner = requirePartner(id);
        if (partner.status() != PartnerStatus.DRAFT) {
            throw new IllegalStateException("Only an unused draft partner revision can be permanently deleted");
        }
        boolean referenced = repository.listAgreements(tenant()).stream()
                .anyMatch(item -> item.partnerId().equals(id) || item.partnerId().equals(partner.familyId()));
        if (referenced) throw new IllegalStateException("Partner revision is used by an agreement");
        repository.deletePartner(tenant(), id);
    }

    public TradingAgreement createAgreement(UUID partnerId, String name) {
        TradingPartner partner = requirePartner(partnerId);
        if (partner.status() != PartnerStatus.ACTIVE) throw new IllegalStateException("Partner is suspended");
        return repository.saveAgreement(TradingAgreement.draft(tenant(), partnerId, name, clock.instant()));
    }

    public TradingAgreement addCapability(UUID agreementId, ExchangeCapability capability) {
        return repository.saveAgreement(requireAgreement(agreementId).addCapability(capability, clock.instant()));
    }

    public TradingAgreement replaceCapability(UUID agreementId, UUID capabilityId, ExchangeCapability capability) {
        return repository.saveAgreement(requireAgreement(agreementId)
                .replaceCapability(capabilityId, capability, clock.instant()));
    }

    public TradingAgreement removeCapability(UUID agreementId, UUID capabilityId) {
        return repository.saveAgreement(requireAgreement(agreementId).removeCapability(capabilityId, clock.instant()));
    }

    public TradingAgreement qualify(UUID agreementId) {
        TradingAgreement agreement = requireAgreement(agreementId);
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        if (agreement.capabilities().isEmpty()) failures.add("At least one exchange capability is required");
        var keys = new HashSet<String>();
        for (ExchangeCapability capability : agreement.capabilities()) {
            checks.add("Unique route " + capability.routingKey());
            if (!keys.add(capability.routingKey())) failures.add("Duplicate route " + capability.routingKey());
            checks.add("Published mapping " + capability.mappingRevisionId());
            if (!qualification.publishedMappingMatches(capability.mappingRevisionId(), capability.sourceSchemaId(),
                    capability.targetSchemaId())) {
                failures.add("Mapping is not published for the capability schemas: " + capability.mappingRevisionId());
            }
            checks.add("Active AS2 connection " + capability.as2ConnectionId());
            if (!qualification.activeAs2ConnectionExists(capability.as2ConnectionId())) {
                failures.add("AS2 connection is not active: " + capability.as2ConnectionId());
            }
            validateStandardProfile(capability, failures);
        }
        QualificationReport report = new QualificationReport(failures.isEmpty(), checks, failures, clock.instant());
        return repository.saveAgreement(agreement.qualify(report, clock.instant()));
    }

    public TradingAgreement activate(UUID agreementId) {
        return activate(agreementId, null);
    }

    public TradingAgreement activate(UUID agreementId, Instant effectiveAt) {
        TradingAgreement candidate = requireAgreement(agreementId);
        Instant now = clock.instant();
        if (effectiveAt != null && effectiveAt.isAfter(now)) {
            return repository.saveAgreement(candidate.schedule(effectiveAt, now));
        }
        repository.listAgreements(tenant()).stream()
                .filter(item -> item.familyId().equals(candidate.familyId()) && !item.id().equals(candidate.id()))
                .map(item -> item.supersede(now)).filter(item -> item.status() == com.edispark.partnerships.domain.model.AgreementStatus.SUPERSEDED)
                .forEach(repository::saveAgreement);
        TradingAgreement active = repository.saveAgreement(candidate.activate(now));
        snapshots.publish(active);
        return active;
    }

    public TradingAgreement suspend(UUID agreementId) {
        return repository.saveAgreement(requireAgreement(agreementId).suspend(clock.instant()));
    }

    public TradingAgreement createRevision(UUID agreementId) {
        return repository.saveAgreement(requireAgreement(agreementId).newRevision(clock.instant()));
    }

    public TradingAgreement archiveAgreement(UUID id) {
        return repository.saveAgreement(requireAgreement(id).archive(clock.instant()));
    }

    public void deleteAgreementDraft(UUID id) {
        TradingAgreement agreement = requireAgreement(id);
        if (agreement.status() != com.edispark.partnerships.domain.model.AgreementStatus.DRAFT) {
            throw new IllegalStateException("Only an unused draft agreement revision can be permanently deleted");
        }
        repository.deleteAgreement(tenant(), id);
    }

    public Usage agreementUsage(UUID id) {
        TradingAgreement agreement = requireAgreement(id);
        boolean deletable = agreement.status() == com.edispark.partnerships.domain.model.AgreementStatus.DRAFT;
        return new Usage(deletable, deletable ? List.of() : List.of(
                new Dependency("agreement-revision", id, "Revision has entered its retained lifecycle")));
    }

    public List<TradingAgreement> listAgreements() { promoteDue(); return repository.listAgreements(tenant()); }
    public TradingAgreement getAgreement(UUID id) { return requireAgreement(id); }

    private TradingPartner requirePartner(UUID id) { return repository.findPartner(tenant(), id).orElseThrow(() -> new IllegalArgumentException("Trading partner was not found")); }
    private TradingAgreement requireAgreement(UUID id) { return repository.findAgreement(tenant(), id).orElseThrow(() -> new IllegalArgumentException("Trading agreement was not found")); }
    private String tenant() { return TenantContext.getCurrentTenant().asString(); }

    private void ensureUniqueIdentities(UUID currentId, List<BusinessIdentity> identities) {
        UUID familyId = requirePartner(currentId).familyId();
        boolean duplicate = repository.listPartners(tenant()).stream().filter(item -> !item.id().equals(currentId))
                .filter(item -> !familyId.equals(item.familyId()))
                .flatMap(item -> item.identities().stream()).anyMatch(identities::contains);
        if (duplicate) throw new IllegalArgumentException("Business identity is already registered");
    }

    private void promoteDue() {
        Instant now = clock.instant();
        repository.listPartners(tenant()).stream()
                .filter(item -> item.status() == PartnerStatus.SCHEDULED && !item.scheduledAt().isAfter(now))
                .forEach(item -> activatePartner(item.id(), null));
        repository.listAgreements(tenant()).stream()
                .filter(item -> item.status() == com.edispark.partnerships.domain.model.AgreementStatus.SCHEDULED)
                .filter(item -> !item.scheduledAt().isAfter(now))
                .map(TradingAgreement::id).toList()
                .forEach(item -> activate(item, null));
    }

    private void validateStandardProfile(ExchangeCapability capability, List<String> failures) {
        String expectedMessage = capability.documentType().name().equals("ORDER") ? "ORDERS" : "INVOIC";
        if (!expectedMessage.equalsIgnoreCase(capability.messageType())) {
            failures.add("Message type " + capability.messageType() + " does not match " + capability.documentType());
        }
        if (capability.standard().name().equals("EDIFACT") && !"D96A".equalsIgnoreCase(capability.release())) {
            failures.add("EDIFACT release is not qualified: " + capability.release());
        }
        if (capability.standard().name().equals("TRADACOMS") && !"9".equals(capability.release())) {
            failures.add("TRADACOMS release is not qualified: " + capability.release());
        }
    }

    public record Usage(boolean deletable, List<Dependency> dependencies) { }
    public record Dependency(String type, UUID id, String label) { }
}
