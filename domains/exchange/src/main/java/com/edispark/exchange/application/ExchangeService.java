package com.edispark.exchange.application;

import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeRepository;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import io.cottn.core.tenancy.TenantContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ExchangeService {
    private final ExchangeRepository exchanges;
    private final PartnershipRepository partnerships;
    private final DocumentAcl documents;
    private final TransformationAcl transformations;
    private final TransportAcl transport;
    private final Clock clock;

    public ExchangeService(ExchangeRepository exchanges, PartnershipRepository partnerships, DocumentAcl documents,
                           TransformationAcl transformations, TransportAcl transport, Clock clock) {
        this.exchanges = exchanges; this.partnerships = partnerships; this.documents = documents;
        this.transformations = transformations; this.transport = transport; this.clock = clock;
    }

    public List<EligibleRoute> eligibleRoutes(UUID documentSetId, UUID documentId) {
        String tenant = tenant();
        var document = documents.describe(documentSetId, documentId);
        return partnerships.listAgreements(tenant).stream()
                .filter(item -> item.status() == AgreementStatus.ACTIVE)
                .flatMap(agreement -> agreement.capabilities().stream()
                        .filter(capability -> capability.direction() == ExchangeDirection.OUTBOUND)
                        .filter(capability -> capability.documentType() == document.type())
                        .map(capability -> new EligibleRoute(agreement.id(), agreement.revision(), agreement.partnerId(),
                                agreement.name(), capability)))
                .toList();
    }

    public EdiExchange submitOutbound(SubmitOutbound command) {
        String tenant = tenant();
        EdiExchange prior = exchanges.findByIdempotencyKey(tenant, command.idempotencyKey()).orElse(null);
        if (prior != null) return prior;
        TradingAgreement agreement = partnerships.findAgreement(tenant, command.agreementId())
                .filter(item -> item.status() == AgreementStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active agreement was not found"));
        ExchangeCapability capability = agreement.capabilities().stream()
                .filter(item -> item.id().equals(command.capabilityId()))
                .filter(item -> item.direction() == ExchangeDirection.OUTBOUND)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Outbound capability was not found"));
        var descriptor = documents.describe(command.documentSetId(), command.documentId());
        if (descriptor.type() != capability.documentType()) throw new IllegalArgumentException("Document type does not match capability");
        EdiExchange exchange = EdiExchange.outbound(tenant, agreement.partnerId(), agreement.id(), agreement.revision(),
                capability.id(), command.idempotencyKey(), descriptor.current(), clock.instant());
        EdiExchange claimed = exchanges.save(exchange);
        if (!claimed.id().equals(exchange.id())) return claimed;
        return process(exchange, capability, descriptor);
    }

    private EdiExchange process(EdiExchange exchange, ExchangeCapability capability, DocumentAcl.DocumentDescriptor descriptor) {
        try {
            exchange.advance(ExchangeStatus.RESOLVING, "Active agreement revision resolved", clock.instant());
            exchange.advance(ExchangeStatus.TRANSFORMING, "Executing published mapping", clock.instant());
            byte[] output = transformations.executePublished(capability, documents.content(descriptor.current()));
            exchange.advance(ExchangeStatus.STORING, "Retaining generated representation", clock.instant());
            DocumentReference derivative = documents.retainDerivative(descriptor.current(), capability.standard().name(),
                    output, capability.mappingRevisionId(), exchange.id());
            exchange.generated(derivative);
            exchange.advance(ExchangeStatus.QUEUING_TRANSPORT, "Submitting to AS2 connection", clock.instant());
            UUID transfer = transport.submit(capability.as2ConnectionId(), exchange.idempotencyKey(),
                    filename(descriptor.businessReference(), capability), contentType(capability), output, exchange.id(),
                    exchange.agreementId(), exchange.agreementRevision(), exchange.capabilityId(),
                    exchange.sourceDocument(), capability.mappingRevisionId());
            exchange.queued(transfer, clock.instant());
            return exchanges.save(exchange);
        } catch (IllegalArgumentException exception) {
            exchange.advance(ExchangeStatus.NEEDS_REVIEW, exception.getMessage(), clock.instant());
            return exchanges.save(exchange);
        } catch (RuntimeException exception) {
            exchange.advance(ExchangeStatus.FAILED, "A transient exchange dependency failed", clock.instant());
            return exchanges.save(exchange);
        }
    }

    public List<EdiExchange> list() { return exchanges.list(tenant()); }
    public EdiExchange get(UUID id) { return exchanges.find(tenant(), id).orElseThrow(() -> new IllegalArgumentException("Exchange was not found")); }

    private String filename(String reference, ExchangeCapability capability) {
        String safe = reference == null ? "document" : reference.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe + "-" + capability.messageType().toLowerCase(java.util.Locale.ROOT) + ".edi";
    }
    private String contentType(ExchangeCapability capability) { return capability.standard().name().equals("EDIFACT") ? "application/edifact" : "application/edi"; }
    private String tenant() { return TenantContext.getCurrentTenant().asString(); }

    public record SubmitOutbound(UUID agreementId, UUID capabilityId, UUID documentSetId, UUID documentId,
                                 String idempotencyKey) { }
    public record EligibleRoute(UUID agreementId, int agreementRevision, UUID partnerId, String agreementName,
                                ExchangeCapability capability) { }
}
