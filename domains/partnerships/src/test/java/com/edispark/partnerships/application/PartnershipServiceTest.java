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
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import com.edispark.partnerships.domain.model.SsccGenerationMode;
import com.edispark.partnerships.infrastructure.InMemoryPartnershipRepository;
import com.edispark.partnerships.infrastructure.InMemorySsccRepository;
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

    @Test void changedTargetRulesRequireRequalificationBeforeActivation() {
        var probe = new QualificationProbe(); probe.ready = true;
        var service = new PartnershipService(new InMemoryPartnershipRepository(), probe, ignored -> { }, CLOCK);
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var partner = service.createPartner("Example Retailer", List.of(new BusinessIdentity("ZZZ", "RETAILER")));
            var agreement = service.createAgreement(partner.id(), "Response route");
            var base = capability();
            service.addCapability(agreement.id(), new ExchangeCapability(base.id(), base.direction(), BusinessDocumentType.ORDER_RESPONSE,
                    base.standard(), base.release(), "ORDRSP", base.sourceSchemaId(), base.targetSchemaId(), base.mappingRevisionId(),
                    base.as2ConnectionId(), base.acknowledgementPolicy(), base.batchingPolicy()));
            assertThat(service.qualify(agreement.id()).qualification().checks()).contains("rules-1");
            probe.ruleIdentity = "rules-2";
            assertThatThrownBy(() -> service.activate(agreement.id())).hasMessageContaining("stale");
            assertThat(service.qualify(agreement.id()).qualification().checks()).contains("rules-2");
            assertThat(service.activate(agreement.id()).status()).isEqualTo(AgreementStatus.ACTIVE);
        });
    }

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

    @Test
    void revisesPartnersAndGuardsRetainedAgreementDeletion() {
        var repository = new InMemoryPartnershipRepository();
        var qualification = new QualificationProbe();
        qualification.ready = true;
        var service = new PartnershipService(repository, qualification, ignored -> { }, CLOCK);
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var partner = service.createPartner("Example Retailer", List.of(new BusinessIdentity("GLN", "[partner_identifier]")));
            var draft = service.createPartnerRevision(partner.id());
            var revised = service.updatePartner(draft.id(), "Example Retailer UK",
                    List.of(new BusinessIdentity("GLN", "[partner_identifier]")));

            assertThat(revised.familyId()).isEqualTo(partner.id());
            assertThat(revised.revision()).isEqualTo(2);
            assertThat(service.activatePartner(revised.id(), CLOCK.instant().plusSeconds(60)).status())
                    .isEqualTo(com.edispark.partnerships.domain.model.PartnerStatus.SCHEDULED);

            var agreement = service.createAgreement(partner.id(), "Retail orders");
            service.deleteAgreementDraft(agreement.id());
            assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT, () -> repository.findAgreement(agreement.id()))).isEmpty();
        });
    }

    @Test
    void defaultsToStrictFilesAndCarriesAnExplicitFailurePolicyThroughRevisions() {
        var service = new PartnershipService(new InMemoryPartnershipRepository(), new QualificationProbe(),
                ignored -> { }, CLOCK);
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var strict = service.createPartner("Strict Retailer",
                    List.of(new BusinessIdentity("GLN", "[strict_partner_identifier]")));
            var tolerant = service.createPartner("Tolerant Retailer",
                    List.of(new BusinessIdentity("GLN", "[tolerant_partner_identifier]")),
                    InboundFileFailurePolicy.KEEP_VALID_MESSAGES);
            var draft = service.createPartnerRevision(tolerant.id());
            var revisedByLegacyClient = service.updatePartner(draft.id(), draft.displayName(), draft.identities(), null);

            assertThat(strict.inboundFileFailurePolicy()).isEqualTo(InboundFileFailurePolicy.REJECT_FILE);
            assertThat(draft.inboundFileFailurePolicy())
                    .isEqualTo(InboundFileFailurePolicy.KEEP_VALID_MESSAGES);
            assertThat(revisedByLegacyClient.inboundFileFailurePolicy())
                    .isEqualTo(InboundFileFailurePolicy.KEEP_VALID_MESSAGES);
        });
    }

    @Test
    void generatedSsccProfilesRequireAnActiveRangeBeforePublication() {
        var ssccRepository = new InMemorySsccRepository();
        var service = new PartnershipService(new InMemoryPartnershipRepository(), new QualificationProbe(),
                ignored -> { }, CLOCK, ssccRepository);
        var ssccService = new SsccService(ssccRepository, CLOCK);
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var partner = service.createPartner("Example Retailer",
                    List.of(new BusinessIdentity("GLN", "[partner_identifier]")));
            var profile = service.createDocumentProfile(partner.id(), "Outbound ASN", ExchangeDirection.OUTBOUND,
                    BusinessDocumentType.DESPATCH_ADVICE, List.of(), SsccGenerationMode.AUTOMATIC, null);

            assertThatThrownBy(() -> service.publishDocumentProfile(profile.id()))
                    .hasMessageContaining("active default SSCC range");

            var issuer = ssccService.createIssuer("Warehouse", "1234567", 0, 1, 100, 12, true, true);
            ssccService.activate(issuer.id());
            assertThat(service.publishDocumentProfile(profile.id()).ssccGenerationMode())
                    .isEqualTo(SsccGenerationMode.AUTOMATIC);
        });
    }

    private ExchangeCapability capability() {
        return new ExchangeCapability(UUID.randomUUID(), ExchangeDirection.OUTBOUND, BusinessDocumentType.ORDER,
                EdiStandard.EDIFACT, "D96A", "ORDERS", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
    }

    private static final class QualificationProbe implements QualificationAcl {
        private boolean ready;
        private String ruleIdentity = "rules-1";
        @Override public RuleEvidence targetRuleEvidence(UUID id) { return new RuleEvidence(ready, ruleIdentity, ready ? List.of() : List.of("Missing target proof")); }
        @Override public boolean publishedMappingMatches(UUID mappingRevisionId, UUID sourceSchemaId, UUID targetSchemaId) { return ready; }
        @Override public boolean activeAs2ConnectionExists(UUID connectionId) { return ready; }
    }
}
