package com.edispark.exchange.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.exchange.domain.ExchangeWarning;
import com.edispark.exchange.infrastructure.InMemoryExchangeRepository;
import com.edispark.identifiers.tenant.TenantId;
import com.edispark.partnerships.domain.model.AcknowledgementPolicy;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.BatchingPolicy;
import com.edispark.partnerships.domain.model.BusinessDocumentType;
import com.edispark.partnerships.domain.model.EdiStandard;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.QualificationReport;
import com.edispark.partnerships.domain.model.OutboundLossPolicy;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.infrastructure.InMemoryPartnershipRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExchangeServiceTest {
    private static final String TENANT = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void readinessIsReadOnlyAndSendingRevalidatesInsteadOfTrustingAnEarlierPass() {
        var partnerships = new InMemoryPartnershipRepository();
        var capability = capability();
        var agreement = agreement(capability);
        saveAgreement(partnerships, agreement);
        var documents = new DocumentProbe();
        var exchanges = new InMemoryExchangeRepository();
        var transformations = new AtomicInteger();
        var transports = new AtomicInteger();
        var service = new ExchangeService(exchanges, partnerships, documents, (route, bytes) -> {
            transformations.incrementAndGet();
            return new TransformationAcl.Result(new byte[]{1}, new byte[]{2}, List.of());
        }, (a, b, c, d, e, f, g, h, i, j, k) -> { transports.incrementAndGet(); return UUID.randomUUID(); }, Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var setId = UUID.randomUUID(); var docId = UUID.randomUUID();
            assertThat(service.checkReadiness(agreement.id(), capability.id(), setId, docId).ready()).isTrue();
            assertThat(service.list()).isEmpty();
            assertThat(documents.retained).hasValue(0);
            documents.issues = List.of(ReadinessIssue.document("SCHEMA_REQUIRED", "/Order/0", "Add the required field."));
            var result = service.submitOutbound(new ExchangeService.SubmitOutbound(agreement.id(), capability.id(), setId, docId, "fresh-check"));
            assertThat(result.status()).isEqualTo(ExchangeStatus.NEEDS_REVIEW);
            assertThat(result.generatedDocument()).isNull();
            assertThat(result.stages()).extracting(stage -> stage.status()).containsExactly(
                    ExchangeStatus.RECEIVED, ExchangeStatus.RESOLVING, ExchangeStatus.VALIDATING, ExchangeStatus.NEEDS_REVIEW);
            assertThat(result.warnings()).extracting(ExchangeWarning::code).containsExactly("SCHEMA_REQUIRED");
            assertThat(transformations).hasValue(1);
            assertThat(transports).hasValue(0);
        });
    }

    @ParameterizedTest
    @EnumSource(EdiStandard.class)
    void retainsEdiAndMappingEvidenceForEveryStandard(EdiStandard standard) {
        var partnerships = new InMemoryPartnershipRepository();
        var capability = capability(standard, OutboundLossPolicy.STRICT);
        var agreement = agreement(capability);
        saveAgreement(partnerships, agreement);
        var transports = new AtomicInteger();
        var documents = new DocumentProbe();
        var service = new ExchangeService(new InMemoryExchangeRepository(), partnerships, documents,
                (route, content) -> new TransformationAcl.Result(
                        "UNH+1+ORDERS:D:96A:UN'UNT+2+1'".getBytes(StandardCharsets.US_ASCII),
                        "{\"warnings\":[]}".getBytes(StandardCharsets.UTF_8), List.of()),
                (connectionId, idempotencyKey, filename, contentType, content, exchangeId, agreementId,
                 agreementRevision, capabilityId, sourceDocument, mappingRevisionId) -> {
                    transports.incrementAndGet();
                    return UUID.randomUUID();
                }, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID documentSetId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        var command = new ExchangeService.SubmitOutbound(agreement.id(), capability.id(), documentSetId,
                documentId, "send-order-once");

        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            var first = service.submitOutbound(command);
            var replay = service.submitOutbound(command);

            assertThat(replay.id()).isEqualTo(first.id());
            assertThat(first.status()).isEqualTo(ExchangeStatus.AWAITING_RECEIPT);
            assertThat(first.generatedDocument()).isNotNull();
            assertThat(first.mftTransferId()).isNotNull();
            assertThat(transports).hasValue(1);
            assertThat(documents.retained).hasValue(2);
            assertThat(documents.retainedArtifacts).containsExactly(
                    DocumentAcl.GeneratedArtifact.EDI_PAYLOAD,
                    DocumentAcl.GeneratedArtifact.MAPPING_REPORT);
        });
    }

    @Test
    void strictPolicyRetainsMappingEvidenceAndStopsBeforeTransportWhenSourceFieldsAreUnmapped() {
        var warning = new ExchangeWarning("SOURCE_FIELD_UNMAPPED", "/Order/0/Note/0/_", "SOURCE_ONLY",
                "A populated source field was not represented by the published mapping");
        var result = submitWithWarning(OutboundLossPolicy.STRICT, warning);

        assertThat(result.exchange().status()).isEqualTo(ExchangeStatus.NEEDS_REVIEW);
        assertThat(result.exchange().generatedDocument()).isNull();
        assertThat(result.exchange().mappingReport()).isNotNull();
        assertThat(result.exchange().warnings()).extracting(ExchangeWarning::code).containsExactly(warning.code());
        assertThat(result.transports()).hasValue(0);
        assertThat(result.documents().retained).hasValue(1);
        assertThat(result.documents().retainedArtifacts).containsExactly(DocumentAcl.GeneratedArtifact.VALIDATION_REPORT);
    }

    @Test
    void allowWithWarningPolicyQueuesTransportAndRetainsTheWarningEvidence() {
        var warning = new ExchangeWarning("SOURCE_FIELD_UNMAPPED", "/Invoice/0/Note/0/_", "SOURCE_ONLY",
                "A populated source field was not represented by the published mapping");
        var result = submitWithWarning(OutboundLossPolicy.ALLOW_WITH_WARNING, warning);

        assertThat(result.exchange().status()).isEqualTo(ExchangeStatus.AWAITING_RECEIPT);
        assertThat(result.exchange().mappingReport()).isNotNull();
        assertThat(result.exchange().warnings()).containsExactly(warning);
        assertThat(result.transports()).hasValue(1);
        assertThat(result.documents().retained).hasValue(2);
    }

    @Test
    void badOutboundDocumentNeedsReviewWithoutBlockingTheNextExport() {
        var partnerships = new InMemoryPartnershipRepository();
        var capability = capability();
        var agreement = agreement(capability);
        saveAgreement(partnerships, agreement);
        var attempts = new AtomicInteger();
        var transports = new AtomicInteger();
        var service = new ExchangeService(new InMemoryExchangeRepository(), partnerships, new DocumentProbe(),
                (route, content) -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalArgumentException("Required outbound field is missing");
                    }
                    return new TransformationAcl.Result(
                            "UNH+1+ORDERS:D:96A:UN'UNT+2+1'".getBytes(StandardCharsets.US_ASCII),
                            "{\"warnings\":[]}".getBytes(StandardCharsets.UTF_8), List.of());
                },
                (connectionId, idempotencyKey, filename, contentType, content, exchangeId, agreementId,
                 agreementRevision, capabilityId, sourceDocument, mappingRevisionId) -> {
                    transports.incrementAndGet();
                    return UUID.randomUUID();
                }, Clock.fixed(NOW, ZoneOffset.UTC));
        var bad = new AtomicReference<EdiExchange>();
        var good = new AtomicReference<EdiExchange>();

        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            bad.set(service.submitOutbound(new ExchangeService.SubmitOutbound(agreement.id(), capability.id(),
                    UUID.randomUUID(), UUID.randomUUID(), "bad-export")));
            good.set(service.submitOutbound(new ExchangeService.SubmitOutbound(agreement.id(), capability.id(),
                    UUID.randomUUID(), UUID.randomUUID(), "good-export")));
        });

        assertThat(bad.get().status()).isEqualTo(ExchangeStatus.NEEDS_REVIEW);
        assertThat(good.get().status()).isEqualTo(ExchangeStatus.AWAITING_RECEIPT);
        assertThat(transports).hasValue(1);
    }

    @Test
    void reviewedInboundMessageCanBeCompletedWithoutChangingItsExchangeIdentity() {
        UUID partnerId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        var exchange = EdiExchange.inboundNeedsReview(TENANT, partnerId, transferId, 2, "42",
                "MESSAGE_REJECTED: no route", NOW);
        UUID originalId = exchange.id();
        var source = new DocumentReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "source-hash");
        var canonical = new DocumentReference(source.documentSetId(), source.documentId(), UUID.randomUUID(),
                "canonical-hash");

        exchange.completeInbound(UUID.randomUUID(), 3, UUID.randomUUID(), source, canonical, NOW.plusSeconds(60));

        assertThat(exchange.id()).isEqualTo(originalId);
        assertThat(exchange.status()).isEqualTo(ExchangeStatus.DELIVERED);
        assertThat(exchange.messageOrdinal()).isEqualTo(2);
        assertThat(exchange.sourceMessageReference()).isEqualTo("42");
        assertThat(exchange.stages()).extracting(item -> item.status())
                .containsExactly(ExchangeStatus.RECEIVED, ExchangeStatus.NEEDS_REVIEW, ExchangeStatus.DELIVERED);
    }

    private ExchangeCapability capability() {
        return capability(OutboundLossPolicy.STRICT);
    }

    private ExchangeCapability capability(OutboundLossPolicy lossPolicy) {
        return capability(EdiStandard.EDIFACT, lossPolicy);
    }

    private ExchangeCapability capability(EdiStandard standard, OutboundLossPolicy lossPolicy) {
        return new ExchangeCapability(UUID.randomUUID(), ExchangeDirection.OUTBOUND, BusinessDocumentType.ORDER,
                standard, standard == EdiStandard.EDIFACT ? "D96A" : "9", "ORDERS", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL, lossPolicy);
    }

    private WarningSubmission submitWithWarning(OutboundLossPolicy policy, ExchangeWarning warning) {
        var partnerships = new InMemoryPartnershipRepository();
        var capability = capability(policy);
        saveAgreement(partnerships, agreement(capability));
        var transports = new AtomicInteger();
        var documents = new DocumentProbe();
        var service = new ExchangeService(new InMemoryExchangeRepository(), partnerships, documents,
                (route, content) -> new TransformationAcl.Result(
                        "UNH+1+ORDERS:D:96A:UN'UNT+2+1'".getBytes(StandardCharsets.US_ASCII),
                        "{\"warnings\":[{\"code\":\"SOURCE_FIELD_UNMAPPED\"}]}".getBytes(StandardCharsets.UTF_8),
                        List.of(warning)),
                (connectionId, idempotencyKey, filename, contentType, content, exchangeId, agreementId,
                 agreementRevision, capabilityId, sourceDocument, mappingRevisionId) -> {
                    transports.incrementAndGet();
                    return UUID.randomUUID();
                }, Clock.fixed(NOW, ZoneOffset.UTC));
        var command = new ExchangeService.SubmitOutbound(
                partnerships.listAgreements(TENANT).getFirst().id(), capability.id(), UUID.randomUUID(),
                UUID.randomUUID(), "warning-" + policy);
        var exchange = new AtomicReference<EdiExchange>();
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)),
                () -> exchange.set(service.submitOutbound(command)));
        return new WarningSubmission(exchange.get(), transports, documents);
    }

    private TradingAgreement agreement(ExchangeCapability capability) {
        return new TradingAgreement(UUID.randomUUID(), TENANT, UUID.randomUUID(), 3, "Retail orders",
                AgreementStatus.ACTIVE, NOW.minusSeconds(60), null, List.of(capability),
                new QualificationReport(true, List.of("mapping", "transport"), List.of(), NOW.minusSeconds(120)),
                NOW.minusSeconds(180), NOW.minusSeconds(60));
    }

    private void saveAgreement(InMemoryPartnershipRepository repository, TradingAgreement agreement) {
        repository.savePartner(new com.edispark.partnerships.domain.model.TradingPartner(agreement.partnerId(), TENANT,
                "Test partner", List.of(), com.edispark.partnerships.domain.model.PartnerStatus.ACTIVE, NOW, NOW));
        repository.saveAgreement(agreement);
    }

    private static final class DocumentProbe implements DocumentAcl {
        private final AtomicInteger retained = new AtomicInteger();
        private final List<GeneratedArtifact> retainedArtifacts = new ArrayList<>();
        private List<ReadinessIssue> issues = List.of();
        @Override public List<ReadinessIssue> validate(DocumentDescriptor descriptor) { return issues; }
        @Override public DocumentDescriptor describe(UUID documentSetId, UUID documentId) {
            return new DocumentDescriptor(new DocumentReference(documentSetId, documentId, UUID.randomUUID(), "source-hash"),
                    BusinessDocumentType.ORDER, "UBL_2_1_JSON", "PO-1001");
        }
        @Override public byte[] content(DocumentReference reference) {
            return "{\"Order\":[]}".getBytes(StandardCharsets.UTF_8);
        }
        @Override public DocumentReference retainDerivative(DocumentReference source, GeneratedArtifact artifact,
                                                              byte[] content, UUID mappingRevisionId, UUID exchangeId) {
            retained.incrementAndGet();
            retainedArtifacts.add(artifact);
            return new DocumentReference(source.documentSetId(), source.documentId(), UUID.randomUUID(), "edi-hash");
        }
    }

    private record WarningSubmission(EdiExchange exchange, AtomicInteger transports, DocumentProbe documents) { }
}
