package com.edispark.integrationapplication.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.partnerships.infrastructure.InMemoryPartnershipRepository;
import com.edispark.partnerships.infrastructure.InMemorySsccRepository;
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import com.edispark.transformations.infrastructure.InMemoryMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LocalIntegrationDemoSeederTest {
    @Test void restartPreservesEditedDraftsAndDeliberatelySuspendedRelationships() {
        var partnerships = new InMemoryPartnershipRepository();
        var seeder = new LocalIntegrationDemoSeeder(partnerships, new InMemorySsccRepository(), new InMemoryMappingRepository(), Clock.systemUTC());
        seeder.reconcile(TENANT_ID, EDIFACT_CONNECTION_ID, TRADACOMS_CONNECTION_ID);
        var draft = io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listPartners()).stream().filter(item -> item.status().name().equals("DRAFT")).findFirst().orElseThrow();
        var edited = draft.revise("Edited local supplier", draft.identities(), draft.inboundFileFailurePolicy(), Instant.now());
        com.edispark.testing.FixtureTenants.persist(edited, record -> record.tenantId(), partnerships::savePartner);
        var active = io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listPartners()).stream().filter(item -> item.status().name().equals("ACTIVE")).findFirst().orElseThrow();
        var suspended = active.suspend(Instant.now()); com.edispark.testing.FixtureTenants.persist(suspended, record -> record.tenantId(), partnerships::savePartner);
        seeder.reconcile(TENANT_ID, EDIFACT_CONNECTION_ID, TRADACOMS_CONNECTION_ID);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.findPartner(edited.id())).orElseThrow()).isEqualTo(edited);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.findPartner(suspended.id())).orElseThrow()).isEqualTo(suspended);
    }
    private static final String TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final UUID EDIFACT_CONNECTION_ID = UUID.fromString("850ca900-3f1e-4f98-b5e2-f17f557cc9e0");
    private static final UUID TRADACOMS_CONNECTION_ID = UUID.fromString("850ca900-3f1e-4f98-b5e2-f17f557cc9e2");

    @Test
    void repeatedReconciliationKeepsStablePartnerAgreementMappingsAndCapabilities() {
        var partnerships = new InMemoryPartnershipRepository();
        var sscc = new InMemorySsccRepository();
        var mappings = new InMemoryMappingRepository();
        var seeder = new LocalIntegrationDemoSeeder(partnerships, sscc, mappings,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC));

        seeder.reconcile(TENANT_ID, EDIFACT_CONNECTION_ID, TRADACOMS_CONNECTION_ID);
        var firstMappingNodes = io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> mappings.list()).stream().flatMap(mapping -> mapping.nodes().stream())
                .map(node -> node.id()).sorted().toList();

        seeder.reconcile(TENANT_ID, EDIFACT_CONNECTION_ID, TRADACOMS_CONNECTION_ID);

        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listPartners())).hasSize(3);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listAgreements())).hasSize(3);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> sscc.list())).singleElement().satisfies(issuer -> {
            assertThat(issuer.name()).isEqualTo("Local demonstration range");
            assertThat(issuer.defaultIssuer()).isTrue();
            assertThat(issuer.nextSerial()).isEqualTo(1);
        });
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> mappings.list())).hasSize(3);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listPartners())).extracting(item -> item.status().name())
                .containsExactlyInAnyOrder("ACTIVE", "ACTIVE", "DRAFT");
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listPartners())).extracting(item -> item.displayName())
                .containsExactlyInAnyOrder("Northstar Retail", "Northstar Retail UK", "Harbour Retail");
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listPartners()).stream()
                .filter(item -> item.displayName().equals("Harbour Retail")))
                .allSatisfy(item -> assertThat(item.inboundFileFailurePolicy())
                        .isEqualTo(InboundFileFailurePolicy.KEEP_VALID_MESSAGES));
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listPartners()).stream()
                .filter(item -> item.displayName().startsWith("Northstar Retail")))
                .allSatisfy(item -> assertThat(item.inboundFileFailurePolicy())
                        .isEqualTo(InboundFileFailurePolicy.REJECT_FILE));
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> mappings.list())).extracting(item -> item.status().name())
                .containsExactlyInAnyOrder("DRAFT", "DRAFT", "SCHEDULED");
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listAgreements()).stream()
                .filter(agreement -> agreement.status().name().equals("ACTIVE")))
                .allSatisfy(agreement -> {
                    assertThat(agreement.capabilities()).hasSize(6);
                    assertThat(agreement.capabilities()).extracting(capability ->
                                    capability.direction().name() + ":" + capability.documentType().name())
                            .containsExactlyInAnyOrder("INBOUND:ORDER", "OUTBOUND:ORDER_RESPONSE",
                                    "OUTBOUND:DESPATCH_ADVICE", "OUTBOUND:INVOICE",
                                    "INBOUND:REMITTANCE_ADVICE", "INBOUND:RECEIPT_ADVICE");
                    assertThat(agreement.capabilities()).extracting(capability -> capability.as2ConnectionId())
                            .containsOnly(agreement.name().contains("EDIFACT")
                                    ? EDIFACT_CONNECTION_ID : TRADACOMS_CONNECTION_ID);
                });
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> partnerships.listAgreements()).stream()
                .filter(agreement -> agreement.status().name().equals("ACTIVE"))
                .flatMap(agreement -> agreement.capabilities().stream())
                .map(capability -> capability.standard().name() + ":" + capability.release()
                        + ":" + capability.direction().name() + ":" + capability.documentType().name()
                        + ":" + capability.mappingRevisionId())
                .toList())
                .containsExactlyInAnyOrder(
                        "EDIFACT:D96A:INBOUND:ORDER:d9c01ea5-f555-4c6d-9c87-1465ea78ac24",
                        "EDIFACT:D96A:OUTBOUND:ORDER_RESPONSE:d9c01ea5-f555-4c6d-9c87-1465ea78ac40",
                        "EDIFACT:D96A:OUTBOUND:DESPATCH_ADVICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac42",
                        "EDIFACT:D96A:OUTBOUND:INVOICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac21",
                        "EDIFACT:D96A:INBOUND:REMITTANCE_ADVICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac54",
                        "EDIFACT:D96A:INBOUND:RECEIPT_ADVICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac56",
                        "TRADACOMS:9:INBOUND:ORDER:d9c01ea5-f555-4c6d-9c87-1465ea78ac26",
                        "TRADACOMS:9:OUTBOUND:ORDER_RESPONSE:d9c01ea5-f555-4c6d-9c87-1465ea78ac41",
                        "TRADACOMS:9:OUTBOUND:DESPATCH_ADVICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac43",
                        "TRADACOMS:9:OUTBOUND:INVOICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac23",
                        "TRADACOMS:9:INBOUND:REMITTANCE_ADVICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac55",
                        "TRADACOMS:9:INBOUND:RECEIPT_ADVICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac57");
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> mappings.list()).stream().flatMap(mapping -> mapping.nodes().stream())
                .map(node -> node.id()).sorted().toList()).isEqualTo(firstMappingNodes);
        var reviewDraft = io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> mappings.list()).stream()
                .filter(mapping -> mapping.name().startsWith("Order response readiness review")).findFirst().orElseThrow();
        io.cottn.core.tenancy.TenantExecution.run(TENANT_ID, () -> mappings.delete(reviewDraft.id()));
        seeder.reconcile(TENANT_ID, EDIFACT_CONNECTION_ID, TRADACOMS_CONNECTION_ID);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> mappings.find(reviewDraft.id())).orElseThrow().nodes()).isEqualTo(reviewDraft.nodes());
        var edited = reviewDraft.revise(reviewDraft.nodes().subList(0, 2));
        com.edispark.testing.FixtureTenants.persist(edited, record -> record.tenantId(), mappings::save);
        seeder.reconcile(TENANT_ID, EDIFACT_CONNECTION_ID, TRADACOMS_CONNECTION_ID);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> mappings.find(reviewDraft.id())).orElseThrow().nodes()).isEqualTo(edited.nodes());
    }
}
