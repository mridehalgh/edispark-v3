package com.edispark.wireup.modules;

import com.edispark.integrationapplication.seed.LocalIntegrationDemoSeeder;
import com.edispark.partnerships.application.PartnershipService;
import com.edispark.partnerships.domain.model.*;
import com.edispark.transformations.application.MappingService;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** A separately named core simulator route; existing six-document agreements retain their pins. */
@Component
@Profile("local & !openapi-export")
@ConditionalOnProperty(name = "integration.local-demo.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
final class LocalOrderResponseDeliveryFixture {
    static final String NAME = "Northstar · Order Response core demo v2";
    static final UUID MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac60");
    private static final UUID CAPABILITY = UUID.nameUUIDFromBytes("local:order-response-core-v2".getBytes(StandardCharsets.UTF_8));
    private final PartnershipService partnerships;
    private final MappingService mappings;

    @EventListener public void seed(LocalIntegrationDemoSeeder.Ready ready) {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(ready.tenantId())), () -> reconcile(ready));
    }

    void reconcile(LocalIntegrationDemoSeeder.Ready ready) {
        if (partnerships.getPartner(ready.edifactPartnerId()).status() != PartnerStatus.ACTIVE) return;
        var evidence = mappings.qualificationEvidence(MAPPING);
        if (!evidence.valid()) throw new IllegalStateException("Core Order Response fixture validation failed");
        var existing = partnerships.listAgreements().stream().filter(item -> item.name().equals(NAME)
                && item.partnerId().equals(ready.edifactPartnerId())).max(Comparator.comparingInt(TradingAgreement::revision)).orElse(null);
        if (current(existing, evidence.identity(), ready.edifactConnectionId())) return;
        if (existing != null && (existing.status() == AgreementStatus.SUSPENDED || existing.status() == AgreementStatus.ARCHIVED)) return;
        var draft = existing == null ? partnerships.createAgreement(ready.edifactPartnerId(), NAME)
                : existing.status() == AgreementStatus.DRAFT || existing.status() == AgreementStatus.QUALIFYING
                        ? existing : partnerships.createRevision(existing.id());
        var capability = new ExchangeCapability(CAPABILITY, ExchangeDirection.OUTBOUND, BusinessDocumentType.ORDER_RESPONSE,
                EdiStandard.EDIFACT, "D96A", "ORDRSP", UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac16"),
                UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac30"), MAPPING, ready.edifactConnectionId(),
                AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
        if (draft.capabilities().stream().noneMatch(item -> item.id().equals(CAPABILITY))) partnerships.addCapability(draft.id(), capability);
        else partnerships.replaceCapability(draft.id(), CAPABILITY, capability);
        partnerships.qualify(draft.id());
        partnerships.activate(draft.id());
    }

    private boolean current(TradingAgreement agreement, String identity, UUID connection) {
        return agreement != null && agreement.status() == AgreementStatus.ACTIVE && agreement.qualification() != null
                && agreement.qualification().passed() && agreement.qualification().checks().contains(identity)
                && agreement.capabilities().stream().anyMatch(item -> item.id().equals(CAPABILITY) && item.as2ConnectionId().equals(connection));
    }
}
