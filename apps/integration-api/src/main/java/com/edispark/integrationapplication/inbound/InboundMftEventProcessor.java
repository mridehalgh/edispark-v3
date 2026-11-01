package com.edispark.integrationapplication.inbound;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Component
@Profile("!openapi-export")
@Slf4j
public final class InboundMftEventProcessor {
    private final PartnershipRepository partnerships;
    private final ExchangeRepository exchanges;
    private final InboundEdiTranslator translator;
    private final RestClient mft;
    private final RestClient documents;
    private final Clock clock;
    private final String internalToken;

    public InboundMftEventProcessor(PartnershipRepository partnerships, ExchangeRepository exchanges,
            InboundEdiTranslator translator,
            @Qualifier("mftRestClient") RestClient mft, @Qualifier("documentsRestClient") RestClient documents,
            Clock clock, @Value("${edispark.internal.token}") String internalToken) {
        this.partnerships = partnerships;
        this.exchanges = exchanges;
        this.translator = translator;
        this.mft = mft;
        this.documents = documents;
        this.clock = clock;
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("Internal service token is required");
        }
        this.internalToken = internalToken;
    }

    public Result process(InboundMftPayloadEvent event) {
        validate(event);
        AtomicReference<Result> result = new AtomicReference<>();
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(event.tenantId())), () -> {
            byte[] payload = fetch(event);
            InboundEdiTranslator.Translation translation = translator.translate(payload);
            Route route = resolve(event, translation);
            result.set(store(event, payload, translation, route));
        });
        Result completed = result.get();
        log.info("Inbound MFT payload processed tenantId={} eventId={} transferId={} agreementId={} "
                        + "capabilityId={} documentSetId={} documentId={} outcome=stored",
                event.tenantId(), event.eventId(), event.transferId(), completed.agreementId(),
                completed.capabilityId(), completed.documentSetId(), completed.documentId());
        return completed;
    }

    private byte[] fetch(InboundMftPayloadEvent event) {
        byte[] payload = mft.get().uri("/internal/mft/transfers/{id}/content", event.transferId())
                .header("X-EdiSpark-Internal-Token", internalToken)
                .header("X-EdiSpark-Tenant-Id", event.tenantId()).retrieve().body(byte[].class);
        if (payload == null || payload.length != event.size()) {
            throw new IllegalArgumentException("Retained MFT payload size does not match its event");
        }
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        if (!MessageDigest.isEqual(hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                event.sha256().toLowerCase().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Retained MFT payload hash does not match its event");
        }
        return payload;
    }

    private Route resolve(InboundMftPayloadEvent event, InboundEdiTranslator.Translation translation) {
        Instant now = clock.instant();
        var matches = partnerships.listAgreements(event.tenantId()).stream()
                .filter(agreement -> active(agreement, now))
                .flatMap(agreement -> agreement.capabilities().stream().map(capability -> new Route(agreement, capability)))
                .filter(route -> route.capability().direction() == ExchangeDirection.INBOUND)
                .filter(route -> route.capability().as2ConnectionId().equals(event.partnerId()))
                .filter(route -> route.capability().standard() == translation.standard())
                .filter(route -> route.capability().release().equalsIgnoreCase(translation.release()))
                .filter(route -> route.capability().messageType().equalsIgnoreCase(translation.messageType()))
                .filter(route -> route.capability().documentType() == translation.documentType()).toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("No active agreement accepts the inbound EDI payload");
        if (matches.size() > 1) throw new IllegalStateException("Inbound EDI route is ambiguous");
        return matches.getFirst();
    }

    private Result store(InboundMftPayloadEvent event, byte[] payload,
            InboundEdiTranslator.Translation translation, Route route) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("raw", new NamedBytes(payload, event.filename()), MediaType.APPLICATION_OCTET_STREAM);
        body.part("canonical", new NamedBytes(translation.canonicalJson(), "canonical.json"), MediaType.APPLICATION_JSON);
        Stored response = documents.post().uri(builder -> builder.path("/internal/documents/inbound-mft")
                        .queryParam("transferId", event.transferId()).queryParam("partnerId", event.partnerId())
                        .queryParam("messageId", event.messageId()).queryParam("filename", event.filename())
                        .queryParam("standard", translation.standard()).queryParam("messageType", translation.messageType())
                        .queryParam("documentType", translation.documentType())
                        .queryParam("receivedAt", event.receivedAt()).build())
                .header("X-EdiSpark-Internal-Token", internalToken)
                .header("X-EdiSpark-Tenant-Id", event.tenantId())
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body.build()).retrieve().body(Stored.class);
        if (response == null) throw new IllegalStateException("Documents did not acknowledge inbound ingestion");
        DocumentReference source = new DocumentReference(response.documentSetId(), response.documentId(),
                response.sourceVersionId(), response.sourceSha256());
        DocumentReference canonical = new DocumentReference(response.documentSetId(), response.documentId(),
                response.canonicalDerivativeId(), response.canonicalSha256());
        EdiExchange exchange = exchanges.save(EdiExchange.inbound(event.tenantId(), route.agreement().partnerId(),
                route.agreement().id(), route.agreement().revision(), route.capability().id(), event.transferId(),
                source, canonical, event.receivedAt()));
        return new Result(exchange.id(), route.agreement().id(), route.capability().id(),
                response.documentSetId(), response.documentId());
    }

    private boolean active(TradingAgreement agreement, Instant now) {
        return agreement.status() == AgreementStatus.ACTIVE
                && (agreement.effectiveFrom() == null || !agreement.effectiveFrom().isAfter(now))
                && (agreement.effectiveUntil() == null || agreement.effectiveUntil().isAfter(now));
    }

    private void validate(InboundMftPayloadEvent event) {
        if (event == null || event.eventId() == null || event.transferId() == null || event.partnerId() == null
                || event.tenantId() == null || event.tenantId().isBlank() || event.messageId() == null
                || event.messageId().isBlank() || event.messageId().length() > 255
                || event.filename() == null || event.filename().isBlank() || event.filename().length() > 255
                || event.sha256() == null || !event.sha256().matches("[0-9a-fA-F]{64}")
                || event.size() < 1 || event.size() > 25L * 1024 * 1024 || event.receivedAt() == null) {
            throw new IllegalArgumentException("Inbound MFT event is invalid");
        }
    }

    private record Route(TradingAgreement agreement, ExchangeCapability capability) { }
    private record Stored(UUID documentSetId, UUID documentId, UUID sourceVersionId, String sourceSha256,
                          UUID canonicalDerivativeId, String canonicalSha256) { }
    public record Result(UUID exchangeId, UUID agreementId, UUID capabilityId, UUID documentSetId, UUID documentId) { }
    private static final class NamedBytes extends ByteArrayResource {
        private final String filename;
        private NamedBytes(byte[] bytes, String filename) { super(bytes); this.filename = filename; }
        @Override public String getFilename() { return filename; }
    }
}
