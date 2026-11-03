package com.edispark.integrationapplication.seed;

import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.BusinessIdentity;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import com.edispark.partnerships.domain.model.PartnerStatus;
import com.edispark.partnerships.domain.model.QualificationReport;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.MappingRepository;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.domain.MappingStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("local & !openapi-export")
@ConditionalOnProperty(name = "integration.local-demo.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public final class LocalIntegrationDemoSeeder {
    private static final UUID PARTNER_ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac01");
    private static final UUID AGREEMENT_ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac02");
    private static final UUID PARTNER_DRAFT_ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac03");
    private static final UUID AGREEMENT_SCHEDULED_ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac04");
    private static final UUID TRADACOMS_PARTNER_ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac05");
    private static final UUID TRADACOMS_AGREEMENT_ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac06");
    private static final UUID ORDER_SOURCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac10");
    private static final UUID INVOICE_SOURCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac11");
    private static final UUID EDIFACT_ORDER_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac12");
    private static final UUID EDIFACT_INVOICE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac13");
    private static final UUID TRADACOMS_ORDER_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac14");
    private static final UUID TRADACOMS_INVOICE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac15");
    private static final UUID EDIFACT_ORDER_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac20");
    private static final UUID EDIFACT_INVOICE_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac21");
    private static final UUID TRADACOMS_ORDER_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac22");
    private static final UUID TRADACOMS_INVOICE_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac23");
    private static final UUID EDIFACT_ORDER_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac24");
    private static final UUID EDIFACT_INVOICE_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac25");
    private static final UUID TRADACOMS_ORDER_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac26");
    private static final UUID TRADACOMS_INVOICE_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac27");
    private static final UUID UNUSED_DRAFT_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac28");
    private static final UUID SCHEDULED_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac29");
    private static final List<UUID> GLOBAL_MAPPING_IDS = List.of(EDIFACT_ORDER_MAPPING, EDIFACT_INVOICE_MAPPING,
            TRADACOMS_ORDER_MAPPING, TRADACOMS_INVOICE_MAPPING, EDIFACT_ORDER_INBOUND_MAPPING,
            EDIFACT_INVOICE_INBOUND_MAPPING, TRADACOMS_ORDER_INBOUND_MAPPING, TRADACOMS_INVOICE_INBOUND_MAPPING);
    private static final String HASH = "8c22a4dd5b19426c04f0cfb8f6ac69e22375b87ef4a78717dc31de72fa941f11";

    private final PartnershipRepository partnerships;
    private final MappingRepository mappings;
    private final Clock clock;

    @Value("${integration.local-demo.tenant-id}") private String tenantId;
    @Value("${integration.local-demo.mft-partner-id-file}") private Path mftPartnerIdFile;
    @Value("${integration.local-demo.mft-tradacoms-partner-id-file}") private Path mftTradacomsPartnerIdFile;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            UUID edifactConnectionId = readPartnerId(mftPartnerIdFile);
            UUID tradacomsConnectionId = readPartnerId(mftTradacomsPartnerIdFile);
            reconcile(tenantId, edifactConnectionId, tradacomsConnectionId);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to seed the local trading network demo", exception);
        }
    }

    private UUID readPartnerId(Path path) throws IOException {
        return UUID.fromString(Files.readString(path).trim());
    }

    void reconcile(String targetTenantId, UUID edifactConnectionId, UUID tradacomsConnectionId) {
        Instant now = clock.instant();
        GLOBAL_MAPPING_IDS.forEach(id -> mappings.delete(targetTenantId, id));
        List<MappingRevision> demoMappings = mappings(targetTenantId, now);
        demoMappings.forEach(mapping -> mappings.find(targetTenantId, mapping.id()).orElseGet(() -> mappings.save(mapping)));
        TradingPartner edifactPartner = partner(PARTNER_ID, targetTenantId, "Northstar Retail", "[edifact_buyer_gln]", now);
        TradingPartner tradacomsPartner = partner(TRADACOMS_PARTNER_ID, targetTenantId, "Harbour Retail",
                "[tradacoms_buyer_gln]", now);
        partnerships.savePartner(edifactPartner);
        partnerships.savePartner(tradacomsPartner);
        partnerships.savePartner(new TradingPartner(PARTNER_DRAFT_ID, targetTenantId, "Northstar Retail UK",
                edifactPartner.identities(), PartnerStatus.DRAFT, now, now, PARTNER_ID, 2, null, null, 1,
                InboundFileFailurePolicy.REJECT_FILE));

        TradingAgreement edifactAgreement = agreement(targetTenantId, PARTNER_ID, AGREEMENT_ID,
                "Northstar EDIFACT order to invoice", EdiStandard.EDIFACT, edifactConnectionId, now);
        TradingAgreement tradacomsAgreement = agreement(targetTenantId, TRADACOMS_PARTNER_ID, TRADACOMS_AGREEMENT_ID,
                "Harbour TRADACOMS order to invoice", EdiStandard.TRADACOMS, tradacomsConnectionId, now);
        partnerships.saveAgreement(edifactAgreement);
        partnerships.saveAgreement(tradacomsAgreement);
        Instant scheduledAt = Instant.parse("2099-01-01T09:00:00Z");
        partnerships.saveAgreement(new TradingAgreement(AGREEMENT_SCHEDULED_ID, targetTenantId, PARTNER_ID, 2,
                edifactAgreement.name(), AgreementStatus.SCHEDULED, scheduledAt, null,
                edifactAgreement.capabilities(), edifactAgreement.qualification(), now, now,
                AGREEMENT_ID, scheduledAt, null, 1));
        log.info("Integration demo ready for tenant {}; two supplier relationships and {} editable mappings",
                targetTenantId, demoMappings.size());
    }

    private TradingPartner partner(UUID id, String targetTenantId, String name, String gln, Instant now) {
        InboundFileFailurePolicy policy = id.equals(TRADACOMS_PARTNER_ID)
                ? InboundFileFailurePolicy.KEEP_VALID_MESSAGES : InboundFileFailurePolicy.REJECT_FILE;
        return new TradingPartner(id, targetTenantId, name,
                List.of(new BusinessIdentity("GLN", gln)), PartnerStatus.ACTIVE, now, now,
                id, 1, null, null, 1, policy);
    }

    private List<MappingRevision> mappings(String targetTenantId, Instant now) {
        return List.of(
                new MappingRevision(UNUSED_DRAFT_MAPPING, targetTenantId, "Draft warehouse order mapping", 1,
                        ORDER_SOURCE_SCHEMA, EDIFACT_ORDER_SCHEMA, MappingStatus.DRAFT, orderNodes(), null, now, null),
                new MappingRevision(SCHEDULED_MAPPING, targetTenantId, "Scheduled invoice mapping", 1,
                        INVOICE_SOURCE_SCHEMA, EDIFACT_INVOICE_SCHEMA, MappingStatus.SCHEDULED, invoiceNodes(), HASH,
                        now, null, SCHEDULED_MAPPING, Instant.parse("2099-01-01T09:00:00Z"), null, 1));
    }

    private List<MappingNode> orderNodes() {
        return nodes("/Order/0/ID/0/_", "/Order/0/IssueDate/0/_",
                "/Order/0/BuyerCustomerParty/0/Party/0/EndpointID/0/_",
                "/Order/0/SellerSupplierParty/0/Party/0/EndpointID/0/_");
    }

    private List<MappingNode> invoiceNodes() {
        return nodes("/Invoice/0/ID/0/_", "/Invoice/0/IssueDate/0/_",
                "/Invoice/0/AccountingSupplierParty/0/Party/0/EndpointID/0/_",
                "/Invoice/0/AccountingCustomerParty/0/Party/0/EndpointID/0/_");
    }

    private List<MappingNode> nodes(String id, String issueDate, String sender, String receiver) {
        List<MappingNode> result = new ArrayList<>();
        result.add(copy(id, "/document/id"));
        result.add(copy(issueDate, "/document/issueDate"));
        result.add(copy(sender, "/parties/sender"));
        result.add(copy(receiver, "/parties/receiver"));
        return List.copyOf(result);
    }

    private MappingNode copy(String source, String target) {
        return new MappingNode(stableId("node:" + source + ":" + target), MappingOperation.COPY,
                List.of(source), target, null, null);
    }

    private TradingAgreement agreement(String targetTenantId, UUID partnerId, UUID agreementId, String name,
            EdiStandard standard, UUID as2ConnectionId, Instant now) {
        boolean edifact = standard == EdiStandard.EDIFACT;
        String release = edifact ? "D96A" : "9";
        UUID orderSchema = edifact ? EDIFACT_ORDER_SCHEMA : TRADACOMS_ORDER_SCHEMA;
        UUID invoiceSchema = edifact ? EDIFACT_INVOICE_SCHEMA : TRADACOMS_INVOICE_SCHEMA;
        UUID inboundOrderMapping = edifact ? EDIFACT_ORDER_INBOUND_MAPPING : TRADACOMS_ORDER_INBOUND_MAPPING;
        UUID outboundInvoiceMapping = edifact ? EDIFACT_INVOICE_MAPPING : TRADACOMS_INVOICE_MAPPING;
        List<ExchangeCapability> capabilities = List.of(
                capability(ExchangeDirection.INBOUND, BusinessDocumentType.ORDER, standard, release,
                        "ORDERS", orderSchema, ORDER_SOURCE_SCHEMA, inboundOrderMapping, as2ConnectionId),
                capability(ExchangeDirection.OUTBOUND, BusinessDocumentType.INVOICE, standard, release,
                        "INVOIC", INVOICE_SOURCE_SCHEMA, invoiceSchema, outboundInvoiceMapping, as2ConnectionId));
        List<String> checks = capabilities.stream().flatMap(item -> List.of(
                "Published mapping " + item.mappingRevisionId(), "Active AS2 connection " + as2ConnectionId).stream()).toList();
        return new TradingAgreement(agreementId, targetTenantId, partnerId, 1, name,
                AgreementStatus.ACTIVE, now, null, capabilities,
                new QualificationReport(true, checks, List.of(), now), now, now);
    }

    private ExchangeCapability capability(ExchangeDirection direction, BusinessDocumentType type,
            EdiStandard standard, String release, String messageType, UUID source, UUID target, UUID mapping,
            UUID connection) {
        UUID id = stableId("capability:" + direction + ":" + type + ":" + standard + ":" + release + ":" + messageType);
        return new ExchangeCapability(id, direction, type, standard, release, messageType,
                source, target, mapping, connection, AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
    }

    private static UUID stableId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
