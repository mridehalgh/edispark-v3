package com.edispark.exchange.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.ExchangeStatus;
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
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.infrastructure.InMemoryPartnershipRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExchangeServiceTest {
    private static final String TENANT = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void retainsTheGeneratedRepresentationAndQueuesTransportExactlyOncePerIdempotencyKey() {
        var partnerships = new InMemoryPartnershipRepository();
        var capability = capability();
        var agreement = agreement(capability);
        partnerships.saveAgreement(agreement);
        var transports = new AtomicInteger();
        var documents = new DocumentProbe();
        var service = new ExchangeService(new InMemoryExchangeRepository(), partnerships, documents,
                (route, content) -> "UNH+1+ORDERS:D:96A:UN'UNT+2+1'".getBytes(StandardCharsets.US_ASCII),
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
            assertThat(documents.retained).hasValue(1);
        });
    }

    private ExchangeCapability capability() {
        return new ExchangeCapability(UUID.randomUUID(), ExchangeDirection.OUTBOUND, BusinessDocumentType.ORDER,
                EdiStandard.EDIFACT, "D96A", "ORDERS", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AcknowledgementPolicy.TRANSPORT_ONLY, BatchingPolicy.INDIVIDUAL);
    }

    private TradingAgreement agreement(ExchangeCapability capability) {
        return new TradingAgreement(UUID.randomUUID(), TENANT, UUID.randomUUID(), 3, "Retail orders",
                AgreementStatus.ACTIVE, NOW.minusSeconds(60), null, List.of(capability),
                new QualificationReport(true, List.of("mapping", "transport"), List.of(), NOW.minusSeconds(120)),
                NOW.minusSeconds(180), NOW.minusSeconds(60));
    }

    private static final class DocumentProbe implements DocumentAcl {
        private final AtomicInteger retained = new AtomicInteger();
        @Override public DocumentDescriptor describe(UUID documentSetId, UUID documentId) {
            return new DocumentDescriptor(new DocumentReference(documentSetId, documentId, UUID.randomUUID(), "source-hash"),
                    BusinessDocumentType.ORDER, "UBL_2_1_JSON", "PO-1001");
        }
        @Override public byte[] content(DocumentReference reference) {
            return "{\"Order\":[]}".getBytes(StandardCharsets.UTF_8);
        }
        @Override public DocumentReference retainDerivative(DocumentReference source, String format, byte[] content,
                                                              UUID mappingRevisionId, UUID exchangeId) {
            retained.incrementAndGet();
            return new DocumentReference(source.documentSetId(), source.documentId(), UUID.randomUUID(), "edi-hash");
        }
    }
}
