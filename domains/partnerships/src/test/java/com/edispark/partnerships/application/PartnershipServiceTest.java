package com.edispark.partnerships.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.BusinessIdentity;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.infrastructure.InMemoryPartnershipRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartnershipServiceTest {
    private static final String TENANT = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void activatesOnlyAfterEveryMappingAndTransportConnectionQualifies() {
        var repository = new InMemoryPartnershipRepository();
        var qualification = new QualificationProbe();
        var published = new ArrayList<UUID>();
        var service = new PartnershipService(repository, qualification,
                agreement -> published.add(agreement.id()), CLOCK);

        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var partner = service.createPartner("Example Retailer", List.of(new BusinessIdentity("GLN", "[partner_identifier]")));
            var agreement = service.createAgreement(partner.id(), "Retail orders");
            var capability = capability();
            service.addCapability(agreement.id(), capability);

            var failed = service.qualify(agreement.id());
            assertThat(failed.qualification().failures()).hasSize(2);
            assertThatThrownBy(() -> service.activate(agreement.id()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Qualification must pass");

            qualification.ready = true;
            var qualified = service.qualify(agreement.id());
            var active = service.activate(agreement.id());

            assertThat(qualified.qualification().passed()).isTrue();
            assertThat(active.status()).isEqualTo(AgreementStatus.ACTIVE);
            assertThat(published).containsExactly(active.id());
            assertThatThrownBy(() -> service.addCapability(active.id(), capability()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("immutable");
        });
    }

    @Test
    void rejectsAnIdentityAlreadyOwnedByAnotherPartnerInTheTenant() {
        var service = new PartnershipService(new InMemoryPartnershipRepository(), new QualificationProbe(), ignored -> { }, CLOCK);
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var identity = new BusinessIdentity("GLN", "[partner_identifier]");
            service.createPartner("First Retailer", List.of(identity));
            assertThatThrownBy(() -> service.createPartner("Second Retailer", List.of(identity)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        });
    }

    private ExchangeCapability capability() {
        return new ExchangeCapability(UUID.randomUUID(), ExchangeDirection.OUTBOUND, BusinessDocumentType.ORDER,
                EdiStandard.EDIFACT, "D96A", "ORDERS", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
    }

    private static final class QualificationProbe implements QualificationAcl {
        private boolean ready;
        @Override public boolean publishedMappingMatches(UUID mappingRevisionId, UUID sourceSchemaId, UUID targetSchemaId) { return ready; }
        @Override public boolean activeAs2ConnectionExists(UUID connectionId) { return ready; }
    }
}
