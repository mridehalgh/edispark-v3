package com.edispark.integrationapplication.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.partnerships.infrastructure.InMemoryPartnershipRepository;
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import com.edispark.transformations.infrastructure.InMemoryMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LocalIntegrationDemoSeederTest {
    private static final String TENANT_ID = "tenant-demo";
    private static final UUID EDIFACT_CONNECTION_ID = UUID.fromString("850ca900-3f1e-4f98-b5e2-f17f557cc9e0");
    private static final UUID TRADACOMS_CONNECTION_ID = UUID.fromString("850ca900-3f1e-4f98-b5e2-f17f557cc9e2");

    @Test
    void repeatedReconciliationKeepsStablePartnerAgreementMappingsAndCapabilities() {
        var partnerships = new InMemoryPartnershipRepository();
        var mappings = new InMemoryMappingRepository();
        var seeder = new LocalIntegrationDemoSeeder(partnerships, mappings,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC));

        seeder.reconcile(TENANT_ID, EDIFACT_CONNECTION_ID, TRADACOMS_CONNECTION_ID);
        var firstMappingNodes = mappings.list(TENANT_ID).stream().flatMap(mapping -> mapping.nodes().stream())
                .map(node -> node.id()).sorted().toList();

        seeder.reconcile(TENANT_ID, EDIFACT_CONNECTION_ID, TRADACOMS_CONNECTION_ID);

        assertThat(partnerships.listPartners(TENANT_ID)).hasSize(3);
        assertThat(partnerships.listAgreements(TENANT_ID)).hasSize(3);
        assertThat(mappings.list(TENANT_ID)).hasSize(2);
        assertThat(partnerships.listPartners(TENANT_ID)).extracting(item -> item.status().name())
                .containsExactlyInAnyOrder("ACTIVE", "ACTIVE", "DRAFT");
        assertThat(partnerships.listPartners(TENANT_ID)).extracting(item -> item.displayName())
                .containsExactlyInAnyOrder("Northstar Retail", "Northstar Retail UK", "Harbour Retail");
        assertThat(partnerships.listPartners(TENANT_ID).stream()
                .filter(item -> item.displayName().equals("Harbour Retail")))
                .allSatisfy(item -> assertThat(item.inboundFileFailurePolicy())
                        .isEqualTo(InboundFileFailurePolicy.KEEP_VALID_MESSAGES));
        assertThat(partnerships.listPartners(TENANT_ID).stream()
                .filter(item -> item.displayName().startsWith("Northstar Retail")))
                .allSatisfy(item -> assertThat(item.inboundFileFailurePolicy())
                        .isEqualTo(InboundFileFailurePolicy.REJECT_FILE));
        assertThat(mappings.list(TENANT_ID)).extracting(item -> item.status().name())
                .containsExactlyInAnyOrder("DRAFT", "SCHEDULED");
        assertThat(partnerships.listAgreements(TENANT_ID).stream()
                .filter(agreement -> agreement.status().name().equals("ACTIVE")))
                .allSatisfy(agreement -> {
                    assertThat(agreement.capabilities()).hasSize(2);
                    assertThat(agreement.capabilities()).extracting(capability ->
                                    capability.direction().name() + ":" + capability.documentType().name())
                            .containsExactlyInAnyOrder("INBOUND:ORDER", "OUTBOUND:INVOICE");
                    assertThat(agreement.capabilities()).extracting(capability -> capability.as2ConnectionId())
                            .containsOnly(agreement.name().contains("EDIFACT")
                                    ? EDIFACT_CONNECTION_ID : TRADACOMS_CONNECTION_ID);
                });
        assertThat(partnerships.listAgreements(TENANT_ID).stream()
                .filter(agreement -> agreement.status().name().equals("ACTIVE"))
                .flatMap(agreement -> agreement.capabilities().stream())
                .map(capability -> capability.standard().name() + ":" + capability.release()
                        + ":" + capability.direction().name() + ":" + capability.documentType().name()
                        + ":" + capability.mappingRevisionId())
                .toList())
                .containsExactlyInAnyOrder(
                        "EDIFACT:D96A:INBOUND:ORDER:d9c01ea5-f555-4c6d-9c87-1465ea78ac24",
                        "EDIFACT:D96A:OUTBOUND:INVOICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac21",
                        "TRADACOMS:9:INBOUND:ORDER:d9c01ea5-f555-4c6d-9c87-1465ea78ac26",
                        "TRADACOMS:9:OUTBOUND:INVOICE:d9c01ea5-f555-4c6d-9c87-1465ea78ac23");
        assertThat(mappings.list(TENANT_ID).stream().flatMap(mapping -> mapping.nodes().stream())
                .map(node -> node.id()).sorted().toList()).isEqualTo(firstMappingNodes);
    }
}
