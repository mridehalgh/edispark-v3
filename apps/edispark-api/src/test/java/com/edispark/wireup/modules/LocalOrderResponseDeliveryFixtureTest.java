package com.edispark.wireup.modules;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.edispark.integrationapplication.seed.LocalIntegrationDemoSeeder;
import com.edispark.partnerships.application.*;
import com.edispark.partnerships.domain.model.*;
import com.edispark.partnerships.infrastructure.InMemoryPartnershipRepository;
import com.edispark.transformations.application.MappingService;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalOrderResponseDeliveryFixtureTest {
    @Test void bqRouteCreatesRepairsAndPreservesSuspensionUnderItsSeparateSimulatorIdentity() {
        String tenant = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
        var repository = new InMemoryPartnershipRepository();
        var rules = new QualificationAcl() {
            public boolean publishedMappingMatches(UUID id, UUID source, UUID target) { return true; }
            public boolean activeAs2ConnectionExists(UUID id) { return true; }
            public RuleEvidence targetRuleEvidence(UUID id) { return new RuleEvidence(true, "bq-proof", List.of()); }
        };
        var partnerships = new PartnershipService(repository, rules, agreement -> { }, Clock.systemUTC());
        var mappings = mock(MappingService.class);
        when(mappings.qualificationEvidence(LocalBqAcknowledgementFixture.MAPPING))
                .thenReturn(new MappingService.QualificationEvidence(true, "bq-proof", List.of()));
        var fixture = new LocalBqAcknowledgementFixture(partnerships, mappings);
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenant)), () -> {
            var ready = new LocalIntegrationDemoSeeder.Ready(tenant, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            fixture.reconcile(ready); fixture.reconcile(ready);
            assertThat(partnerships.listPartners()).hasSize(1);
            assertThat(partnerships.listAgreements()).hasSize(1);
            var agreement = partnerships.listAgreements().getFirst();
            assertThat(agreement.status()).isEqualTo(AgreementStatus.ACTIVE);
            assertThat(agreement.capabilities()).singleElement().satisfies(cap ->
                    assertThat(cap.mappingRevisionId()).isEqualTo(LocalBqAcknowledgementFixture.MAPPING));
            io.cottn.core.tenancy.TenantExecution.run(tenant, () -> repository.deleteAgreement(agreement.id()));
            fixture.reconcile(ready);
            assertThat(partnerships.listAgreements()).hasSize(1);
            var replacement = partnerships.listAgreements().getFirst();
            partnerships.suspend(replacement.id());
            fixture.reconcile(ready);
            assertThat(partnerships.listAgreements()).singleElement()
                    .satisfies(item -> assertThat(item.status()).isEqualTo(AgreementStatus.SUSPENDED));
        });
    }
    @Test void createsRepairsAndReconcilesOnlyItsVersionedCoreRoute() {
        String tenant = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
        var repository = new InMemoryPartnershipRepository();
        var rules = new QualificationAcl() {
            public boolean publishedMappingMatches(UUID id, UUID source, UUID target) { return true; }
            public boolean activeAs2ConnectionExists(UUID id) { return true; }
            public RuleEvidence targetRuleEvidence(UUID id) { return new RuleEvidence(true, "core-proof", List.of()); }
        };
        var partnerships = new PartnershipService(repository, rules, agreement -> { }, Clock.systemUTC());
        var mappings = mock(MappingService.class);
        when(mappings.qualificationEvidence(LocalOrderResponseDeliveryFixture.MAPPING))
                .thenReturn(new MappingService.QualificationEvidence(true, "core-proof", List.of()));
        var fixture = new LocalOrderResponseDeliveryFixture(partnerships, mappings);
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenant)), () -> {
            var partner = partnerships.createPartner("Local Retailer", List.of(new BusinessIdentity("ZZZ", "RETAILER")));
            var unrelated = partnerships.createAgreement(partner.id(), "Developer-created route");
            var ready = new LocalIntegrationDemoSeeder.Ready(tenant, partner.id(), UUID.randomUUID());
            fixture.reconcile(ready);
            var created = partnerships.listAgreements().stream().filter(item -> item.name().equals(LocalOrderResponseDeliveryFixture.NAME)).findFirst().orElseThrow();
            assertThat(created.status()).isEqualTo(AgreementStatus.ACTIVE);
            assertThat(created.capabilities()).singleElement().satisfies(cap -> assertThat(cap.mappingRevisionId()).isEqualTo(LocalOrderResponseDeliveryFixture.MAPPING));
            fixture.reconcile(ready);
            assertThat(partnerships.listAgreements()).hasSize(2);
            io.cottn.core.tenancy.TenantExecution.run(tenant, () -> repository.deleteAgreement(created.id()));
            fixture.reconcile(ready);
            assertThat(partnerships.listAgreements()).hasSize(2);
            assertThat(partnerships.getAgreement(unrelated.id())).isEqualTo(unrelated);
        });
    }
}
