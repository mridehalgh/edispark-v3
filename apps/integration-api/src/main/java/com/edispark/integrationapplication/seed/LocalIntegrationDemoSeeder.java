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
import com.edispark.partnerships.domain.model.SsccIssuer;
import com.edispark.partnerships.domain.model.SsccIssuerStatus;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import com.edispark.partnerships.domain.repository.SsccRepository;
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
    private static final UUID LOCAL_SSCC_ISSUER_ID = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac07");
    private static final UUID ORDER_SOURCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac10");
    private static final UUID INVOICE_SOURCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac11");
    private static final UUID EDIFACT_ORDER_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac12");
    private static final UUID EDIFACT_INVOICE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac13");
    private static final UUID TRADACOMS_ORDER_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac14");
    private static final UUID TRADACOMS_INVOICE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac15");
    private static final UUID ORDER_RESPONSE_SOURCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac16");
    private static final UUID DESPATCH_SOURCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac17");
    private static final UUID REMITTANCE_SOURCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac18");
    private static final UUID RECEIPT_SOURCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac19");
    private static final UUID EDIFACT_ORDER_RESPONSE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac30");
    private static final UUID EDIFACT_DESPATCH_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac31");
    private static final UUID EDIFACT_REMITTANCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac32");
    private static final UUID EDIFACT_RECEIPT_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac33");
    private static final UUID TRADACOMS_ORDER_RESPONSE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac34");
    private static final UUID TRADACOMS_DESPATCH_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac35");
    private static final UUID TRADACOMS_REMITTANCE_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac36");
    private static final UUID TRADACOMS_RECEIPT_SCHEMA = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac37");
    private static final UUID EDIFACT_ORDER_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac20");
    private static final UUID EDIFACT_INVOICE_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac21");
    private static final UUID TRADACOMS_ORDER_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac22");
    private static final UUID TRADACOMS_INVOICE_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac23");
    private static final UUID EDIFACT_ORDER_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac24");
    private static final UUID EDIFACT_INVOICE_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac25");
    private static final UUID TRADACOMS_ORDER_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac26");
    private static final UUID TRADACOMS_INVOICE_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac27");
    private static final UUID EDIFACT_ORDER_RESPONSE_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac40");
    private static final UUID TRADACOMS_ORDER_RESPONSE_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac41");
    private static final UUID EDIFACT_DESPATCH_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac42");
    private static final UUID TRADACOMS_DESPATCH_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac43");
    private static final UUID EDIFACT_REMITTANCE_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac54");
    private static final UUID TRADACOMS_REMITTANCE_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac55");
    private static final UUID EDIFACT_RECEIPT_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac56");
    private static final UUID TRADACOMS_RECEIPT_INBOUND_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac57");
    private static final UUID UNUSED_DRAFT_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac28");
    private static final UUID SCHEDULED_MAPPING = UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac29");
    private static final List<UUID> GLOBAL_MAPPING_IDS = List.of(EDIFACT_ORDER_MAPPING, EDIFACT_INVOICE_MAPPING,
            TRADACOMS_ORDER_MAPPING, TRADACOMS_INVOICE_MAPPING, EDIFACT_ORDER_INBOUND_MAPPING,
            EDIFACT_INVOICE_INBOUND_MAPPING, TRADACOMS_ORDER_INBOUND_MAPPING, TRADACOMS_INVOICE_INBOUND_MAPPING);
    private static final String HASH = "8c22a4dd5b19426c04f0cfb8f6ac69e22375b87ef4a78717dc31de72fa941f11";

    private final PartnershipRepository partnerships;
    private final SsccRepository sscc;
    private final MappingRepository mappings;
    private final Clock clock;

    @Value("${integration.local-demo.tenant-id}") private String tenantId;
    @Value("${integration.local-demo.mft-partner-id-file}") private Path mftPartnerIdFile;
    @Value("${integration.local-demo.mft-tradacoms-partner-id-file}") private Path mftTradacomsPartnerIdFile;

    @EventListener(ApplicationReadyEvent.class)
    public Ready seed() {
        try {
            UUID edifactConnectionId = readPartnerId(mftPartnerIdFile);
            UUID tradacomsConnectionId = readPartnerId(mftTradacomsPartnerIdFile);
            reconcile(tenantId, edifactConnectionId, tradacomsConnectionId);
            return new Ready(tenantId, PARTNER_ID, edifactConnectionId, tradacomsConnectionId);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to seed the local trading network demo", exception);
        }
    }

    /** Local-only fixture completion; emitted synchronously after both partner records exist. */
    public record Ready(String tenantId, UUID edifactPartnerId, UUID edifactConnectionId, UUID tradacomsConnectionId) {
        public Ready(String tenantId, UUID edifactPartnerId, UUID edifactConnectionId) {
            this(tenantId, edifactPartnerId, edifactConnectionId, null);
        }
    }

    private UUID readPartnerId(Path path) throws IOException {
        return UUID.fromString(Files.readString(path).trim());
    }

    void reconcile(String targetTenantId, UUID edifactConnectionId, UUID tradacomsConnectionId) {
        io.cottn.core.tenancy.TenantExecution.run(targetTenantId,
                () -> reconcileTenant(targetTenantId, edifactConnectionId, tradacomsConnectionId));
    }

    private void reconcileTenant(String targetTenantId, UUID edifactConnectionId, UUID tradacomsConnectionId) {
        Instant now = clock.instant();
        GLOBAL_MAPPING_IDS.forEach(id -> mappings.delete(id));
        List<MappingRevision> demoMappings = mappings(targetTenantId, now);
        demoMappings.forEach(mapping -> mappings.find(mapping.id()).orElseGet(() -> mappings.save(mapping)));
        TradingPartner edifactPartner = partner(PARTNER_ID, targetTenantId, "Northstar Retail", "[edifact_buyer_gln]", now);
        TradingPartner tradacomsPartner = partner(TRADACOMS_PARTNER_ID, targetTenantId, "Harbour Retail",
                "[tradacoms_buyer_gln]", now);
        retainPartner(edifactPartner);
        retainPartner(tradacomsPartner);
        retainPartner(new TradingPartner(PARTNER_DRAFT_ID, targetTenantId, "Northstar Retail UK",
                edifactPartner.identities(), PartnerStatus.DRAFT, now, now, PARTNER_ID, 2, null, null, 1,
                InboundFileFailurePolicy.REJECT_FILE));
        sscc.find(LOCAL_SSCC_ISSUER_ID).orElseGet(() -> sscc.save(new SsccIssuer(
                LOCAL_SSCC_ISSUER_ID, targetTenantId, "Local demonstration range", "1234567", 0,
                1, 999999999L, 1, 12, true, true, SsccIssuerStatus.ACTIVE, 0, now, now)));

        TradingAgreement edifactAgreement = agreement(targetTenantId, PARTNER_ID, AGREEMENT_ID,
                "Northstar EDIFACT order to invoice", EdiStandard.EDIFACT, edifactConnectionId, now);
        TradingAgreement tradacomsAgreement = agreement(targetTenantId, TRADACOMS_PARTNER_ID, TRADACOMS_AGREEMENT_ID,
                "Harbour TRADACOMS order to invoice", EdiStandard.TRADACOMS, tradacomsConnectionId, now);
        retainAgreement(edifactAgreement);
        retainAgreement(tradacomsAgreement);
        Instant scheduledAt = Instant.parse("2099-01-01T09:00:00Z");
        retainAgreement(new TradingAgreement(AGREEMENT_SCHEDULED_ID, targetTenantId, PARTNER_ID, 2,
                edifactAgreement.name(), AgreementStatus.SCHEDULED, scheduledAt, null,
                edifactAgreement.capabilities(), edifactAgreement.qualification(), now, now,
                AGREEMENT_ID, scheduledAt, null, 1));
        log.info("Integration demo ready for tenant {}; two supplier relationships, a synthetic SSCC range and {} editable mappings",
                targetTenantId, demoMappings.size());
    }

    private void retainPartner(TradingPartner fixture) {
        partnerships.findPartner(fixture.id()).orElseGet(() -> partnerships.savePartner(fixture));
    }

    private void retainAgreement(TradingAgreement fixture) {
        partnerships.findAgreement(fixture.id()).orElseGet(() -> partnerships.saveAgreement(fixture));
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
                        now, null, SCHEDULED_MAPPING, Instant.parse("2099-01-01T09:00:00Z"), null, 1),
                new MappingRevision(stableId("readiness:order-response:repeated-lines"), targetTenantId,
                        "Order response readiness review — every line", 1,
                        UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac16"),
                        UUID.fromString("d9c01ea5-f555-4c6d-9c87-1465ea78ac34"), MappingStatus.DRAFT,
                        responseReviewNodes(), null, now, null));
    }

    private List<MappingNode> responseReviewNodes() {
        var nodes = new ArrayList<>(nodes("/OrderResponse/0/ID/0/_", "/OrderResponse/0/IssueDate/0/_",
                "/OrderResponse/0/SellerSupplierParty/0/Party/0/EndpointID/0/_",
                "/OrderResponse/0/BuyerCustomerParty/0/Party/0/EndpointID/0/_"));
        nodes.add(copy("/OrderResponse/0/OrderReference/0/ID/0/_", "/document/orderReference"));
        String line = "/OrderResponse/0/OrderLine/*/LineItem/0";
        nodes.add(copy(line + "/ID/0/_", "/lines/*/id"));
        nodes.add(copy(line + "/Quantity/0/_", "/lines/*/quantity"));
        nodes.add(copy(line + "/Quantity/0/unitCode", "/lines/*/unitCode"));
        return List.copyOf(nodes);
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
        UUID orderResponseSchema = edifact ? EDIFACT_ORDER_RESPONSE_SCHEMA : TRADACOMS_ORDER_RESPONSE_SCHEMA;
        UUID despatchSchema = edifact ? EDIFACT_DESPATCH_SCHEMA : TRADACOMS_DESPATCH_SCHEMA;
        UUID remittanceSchema = edifact ? EDIFACT_REMITTANCE_SCHEMA : TRADACOMS_REMITTANCE_SCHEMA;
        UUID receiptSchema = edifact ? EDIFACT_RECEIPT_SCHEMA : TRADACOMS_RECEIPT_SCHEMA;
        List<ExchangeCapability> capabilities = List.of(
                capability(ExchangeDirection.INBOUND, BusinessDocumentType.ORDER, standard, release,
                        "ORDERS", orderSchema, ORDER_SOURCE_SCHEMA, inboundOrderMapping, as2ConnectionId),
                capability(ExchangeDirection.OUTBOUND, BusinessDocumentType.ORDER_RESPONSE, standard, release,
                        "ORDRSP", ORDER_RESPONSE_SOURCE_SCHEMA, orderResponseSchema,
                        edifact ? EDIFACT_ORDER_RESPONSE_MAPPING : TRADACOMS_ORDER_RESPONSE_MAPPING, as2ConnectionId),
                capability(ExchangeDirection.OUTBOUND, BusinessDocumentType.DESPATCH_ADVICE, standard, release,
                        "DESADV", DESPATCH_SOURCE_SCHEMA, despatchSchema,
                        edifact ? EDIFACT_DESPATCH_MAPPING : TRADACOMS_DESPATCH_MAPPING, as2ConnectionId),
                capability(ExchangeDirection.OUTBOUND, BusinessDocumentType.INVOICE, standard, release,
                        "INVOIC", INVOICE_SOURCE_SCHEMA, invoiceSchema, outboundInvoiceMapping, as2ConnectionId),
                capability(ExchangeDirection.INBOUND, BusinessDocumentType.REMITTANCE_ADVICE, standard, release,
                        "REMADV", remittanceSchema, REMITTANCE_SOURCE_SCHEMA,
                        edifact ? EDIFACT_REMITTANCE_INBOUND_MAPPING : TRADACOMS_REMITTANCE_INBOUND_MAPPING, as2ConnectionId),
                capability(ExchangeDirection.INBOUND, BusinessDocumentType.RECEIPT_ADVICE, standard, release,
                        "RECADV", receiptSchema, RECEIPT_SOURCE_SCHEMA,
                        edifact ? EDIFACT_RECEIPT_INBOUND_MAPPING : TRADACOMS_RECEIPT_INBOUND_MAPPING, as2ConnectionId));
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
