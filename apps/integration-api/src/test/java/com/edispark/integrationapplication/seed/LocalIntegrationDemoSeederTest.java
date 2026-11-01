package com.edispark.integrationapplication.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.partnerships.infrastructure.InMemoryPartnershipRepository;
import com.edispark.transformations.infrastructure.InMemoryMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LocalIntegrationDemoSeederTest {
    private static final String TENANT_ID = "tenant-demo";
    private static final UUID AS2_CONNECTION_ID = UUID.fromString("850ca900-3f1e-4f98-b5e2-f17f557cc9e0");

    @Test
    void repeatedReconciliationKeepsStablePartnerAgreementMappingsAndCapabilities() {
        var partnerships = new InMemoryPartnershipRepository();
        var mappings = new InMemoryMappingRepository();
        var seeder = new LocalIntegrationDemoSeeder(partnerships, mappings,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC));

        seeder.reconcile(TENANT_ID, AS2_CONNECTION_ID);
        var firstAgreement = partnerships.listAgreements(TENANT_ID).getFirst();
        var firstMappingNodes = mappings.list(TENANT_ID).stream().flatMap(mapping -> mapping.nodes().stream())
                .map(node -> node.id()).sorted().toList();

        seeder.reconcile(TENANT_ID, AS2_CONNECTION_ID);

        assertThat(partnerships.listPartners(TENANT_ID)).hasSize(1);
        assertThat(partnerships.listAgreements(TENANT_ID)).hasSize(1);
        assertThat(mappings.list(TENANT_ID)).hasSize(4);
        assertThat(partnerships.listAgreements(TENANT_ID).getFirst().capabilities())
                .extracting(capability -> capability.id())
                .containsExactlyElementsOf(firstAgreement.capabilities().stream().map(capability -> capability.id()).toList());
        assertThat(mappings.list(TENANT_ID).stream().flatMap(mapping -> mapping.nodes().stream())
                .map(node -> node.id()).sorted().toList()).isEqualTo(firstMappingNodes);
    }
}
