package com.edispark.partnerships.application;

import com.edispark.partnerships.domain.model.BusinessIdentity;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.PartnerStatus;
import com.edispark.partnerships.domain.model.QualificationReport;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import io.cottn.core.tenancy.TenantContext;
import java.time.Clock;
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

    public TradingPartner createPartner(String name, List<BusinessIdentity> identities) {
        String tenant = tenant();
        for (BusinessIdentity identity : identities) {
            boolean duplicate = repository.listPartners(tenant).stream()
                    .flatMap(item -> item.identities().stream()).anyMatch(identity::equals);
            if (duplicate) throw new IllegalArgumentException("Business identity is already registered");
        }
        return repository.savePartner(TradingPartner.create(tenant, name, identities, clock.instant()));
    }

    public List<TradingPartner> listPartners() { return repository.listPartners(tenant()); }

    public TradingAgreement createAgreement(UUID partnerId, String name) {
        TradingPartner partner = requirePartner(partnerId);
        if (partner.status() != PartnerStatus.ACTIVE) throw new IllegalStateException("Partner is suspended");
        return repository.saveAgreement(TradingAgreement.draft(tenant(), partnerId, name, clock.instant()));
    }

    public TradingAgreement addCapability(UUID agreementId, ExchangeCapability capability) {
        return repository.saveAgreement(requireAgreement(agreementId).addCapability(capability, clock.instant()));
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
        TradingAgreement active = repository.saveAgreement(requireAgreement(agreementId).activate(clock.instant()));
        snapshots.publish(active);
        return active;
    }

    public TradingAgreement suspend(UUID agreementId) {
        return repository.saveAgreement(requireAgreement(agreementId).suspend(clock.instant()));
    }

    public TradingAgreement createRevision(UUID agreementId) {
        return repository.saveAgreement(requireAgreement(agreementId).newRevision(clock.instant()));
    }

    public List<TradingAgreement> listAgreements() { return repository.listAgreements(tenant()); }
    public TradingAgreement getAgreement(UUID id) { return requireAgreement(id); }

    private TradingPartner requirePartner(UUID id) { return repository.findPartner(tenant(), id).orElseThrow(() -> new IllegalArgumentException("Trading partner was not found")); }
    private TradingAgreement requireAgreement(UUID id) { return repository.findAgreement(tenant(), id).orElseThrow(() -> new IllegalArgumentException("Trading agreement was not found")); }
    private String tenant() { return TenantContext.getCurrentTenant().asString(); }

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
}
