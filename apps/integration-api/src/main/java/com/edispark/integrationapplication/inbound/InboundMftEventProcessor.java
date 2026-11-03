package com.edispark.integrationapplication.inbound;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.partnerships.domain.model.AgreementStatus;
import com.edispark.partnerships.domain.model.ExchangeCapability;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.edispark.partnerships.domain.model.InboundFileFailurePolicy;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.edispark.partnerships.domain.model.TradingPartner;
import com.edispark.partnerships.domain.repository.PartnershipRepository;
import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeRepository;
import com.edispark.transformations.application.MappingService;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
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
    private final MappingService mappings;
    private final RestClient mft;
    private final RestClient documents;
    private final Clock clock;
    private final String internalToken;

    public InboundMftEventProcessor(PartnershipRepository partnerships, ExchangeRepository exchanges,
            InboundEdiTranslator translator,
            MappingService mappings,
            @Qualifier("mftRestClient") RestClient mft, @Qualifier("documentsRestClient") RestClient documents,
            Clock clock, @Value("${edispark.internal.token}") String internalToken) {
        this.partnerships = partnerships;
        this.exchanges = exchanges;
        this.translator = translator;
        this.mappings = mappings;
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
            TradingPartner partner = resolvePartner(event);
            List<InboundEdiBatchSplitter.InboundMessage> messages = translator.split(payload);
            result.set(partner.inboundFileFailurePolicy() == InboundFileFailurePolicy.REJECT_FILE
                    ? processStrict(event, payload, partner, messages)
                    : processBestEffort(event, payload, partner, messages));
        });
        Result completed = result.get();
        log.info("Inbound MFT payload processed tenantId={} eventId={} transferId={} messageCount={} "
                        + "storedCount={} reviewCount={} outcome=completed",
                event.tenantId(), event.eventId(), event.transferId(), completed.messages().size(),
                completed.storedCount(), completed.reviewCount());
        return completed;
    }

    private Result processStrict(InboundMftPayloadEvent event, byte[] payload, TradingPartner partner,
            List<InboundEdiBatchSplitter.InboundMessage> messages) {
        List<Prepared> prepared = messages.stream().map(message -> prepare(event, partner, message)).toList();
        return result(prepared.stream().map(item -> store(event, payload, item)).toList());
    }

    private Result processBestEffort(InboundMftPayloadEvent event, byte[] payload, TradingPartner partner,
            List<InboundEdiBatchSplitter.InboundMessage> messages) {
        List<MessageResult> results = new ArrayList<>();
        for (InboundEdiBatchSplitter.InboundMessage message : messages) {
            try {
                results.add(store(event, payload, prepare(event, partner, message)));
            } catch (IllegalArgumentException exception) {
                results.add(review(event, partner, message, exception));
            }
        }
        return result(results);
    }

    private Prepared prepare(InboundMftPayloadEvent event, TradingPartner partner,
            InboundEdiBatchSplitter.InboundMessage message) {
        InboundEdiTranslator.Translation translation = translator.translate(message);
        Route route = resolve(event, partner, translation);
        byte[] canonical = mappings.executePublished(
                route.capability().mappingRevisionId(), translation.mappingInputJson());
        return new Prepared(translation, route, canonical);
    }

    private Result result(List<MessageResult> messages) {
        int reviews = (int) messages.stream().filter(item -> item.status() == MessageStatus.NEEDS_REVIEW).count();
        return new Result(List.copyOf(messages), messages.size() - reviews, reviews);
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

    private TradingPartner resolvePartner(InboundMftPayloadEvent event) {
        Instant now = clock.instant();
        List<TradingPartner> candidates = partnerships.listAgreements(event.tenantId()).stream()
                .filter(agreement -> active(agreement, now))
                .filter(agreement -> agreement.capabilities().stream().anyMatch(capability ->
                        capability.direction() == ExchangeDirection.INBOUND
                                && capability.as2ConnectionId().equals(event.partnerId())))
                .map(TradingAgreement::partnerId)
                .map(id -> partnerships.findPartner(event.tenantId(), id).orElseThrow(() ->
                        new IllegalStateException("Inbound agreement partner was not found")))
                .map(partner -> currentPartner(event.tenantId(), partner)).distinct().toList();
        if (candidates.isEmpty()) throw new IllegalArgumentException("No active partner accepts the inbound connection");
        if (candidates.size() > 1) throw new IllegalStateException("Inbound connection partner is ambiguous");
        return candidates.getFirst();
    }

    private TradingPartner currentPartner(String tenantId, TradingPartner partner) {
        return partnerships.listPartners(tenantId).stream()
                .filter(item -> item.familyId().equals(partner.familyId()))
                .filter(item -> item.status() == com.edispark.partnerships.domain.model.PartnerStatus.ACTIVE)
                .findFirst().orElse(partner);
    }

    private Route resolve(InboundMftPayloadEvent event, TradingPartner partner,
            InboundEdiTranslator.Translation translation) {
        Instant now = clock.instant();
        var matches = partnerships.listAgreements(event.tenantId()).stream()
                .filter(agreement -> active(agreement, now))
                .filter(agreement -> belongsTo(event.tenantId(), agreement, partner))
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

    private boolean belongsTo(String tenantId, TradingAgreement agreement, TradingPartner partner) {
        return partnerships.findPartner(tenantId, agreement.partnerId())
                .map(item -> item.familyId().equals(partner.familyId())).orElse(false);
    }

    private MessageResult store(InboundMftPayloadEvent event, byte[] payload, Prepared prepared) {
        InboundEdiTranslator.Translation translation = prepared.translation();
        byte[] raw = translation.messageCount() == 1 ? payload : translation.rawMessage();
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("raw", new NamedBytes(raw, messageFilename(event.filename(), translation)),
                MediaType.APPLICATION_OCTET_STREAM);
        body.part("canonical", new NamedBytes(prepared.canonical(), "canonical.json"), MediaType.APPLICATION_JSON);
        Stored response = documents.post().uri(builder -> builder.path("/internal/documents/inbound-mft")
                        .queryParam("transferId", event.transferId()).queryParam("partnerId", event.partnerId())
                        .queryParam("messageId", event.messageId()).queryParam("filename", event.filename())
                        .queryParam("messageOrdinal", translation.messageOrdinal())
                        .queryParam("messageCount", translation.messageCount())
                        .queryParam("sourceMessageReference", translation.sourceMessageReference())
                        .queryParam("standard", translation.standard()).queryParam("messageType", translation.messageType())
                        .queryParam("documentType", translation.documentType())
                        .queryParam("receivedAt", event.receivedAt()).build())
                .header("X-EdiSpark-Internal-Token", internalToken)
                .header("X-EdiSpark-Tenant-Id", event.tenantId())
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body.build()).retrieve().body(Stored.class);
        if (response == null) throw new IllegalStateException("Documents did not acknowledge inbound ingestion");
        DocumentReference source = new DocumentReference(response.documentSetId(), response.documentId(),
                response.sourceVersionId(), response.sourceSha256());
        DocumentReference canonicalDocument = new DocumentReference(response.documentSetId(), response.documentId(),
                response.canonicalDerivativeId(), response.canonicalSha256());
        Route route = prepared.route();
        String key = inboundKey(event.transferId(), translation.messageOrdinal());
        EdiExchange exchange = exchanges.findByIdempotencyKey(event.tenantId(), key).orElse(null);
        if (exchange != null && exchange.status() == com.edispark.exchange.domain.ExchangeStatus.NEEDS_REVIEW) {
            exchange.completeInbound(route.agreement().id(), route.agreement().revision(), route.capability().id(),
                    source, canonicalDocument, clock.instant());
            exchange = exchanges.save(exchange);
        } else if (exchange == null) {
            exchange = exchanges.save(EdiExchange.inbound(event.tenantId(), route.agreement().partnerId(),
                    route.agreement().id(), route.agreement().revision(), route.capability().id(), event.transferId(),
                    source, canonicalDocument, translation.messageOrdinal(), translation.sourceMessageReference(),
                    event.receivedAt()));
        }
        return new MessageResult(translation.messageOrdinal(), translation.sourceMessageReference(), exchange.id(),
                response.documentSetId(), response.documentId(), MessageStatus.STORED);
    }

    private MessageResult review(InboundMftPayloadEvent event, TradingPartner partner,
            InboundEdiBatchSplitter.InboundMessage message, IllegalArgumentException exception) {
        String reason = "MESSAGE_REJECTED: " + bounded(exception.getMessage());
        EdiExchange review = exchanges.save(EdiExchange.inboundNeedsReview(event.tenantId(), partner.id(),
                event.transferId(), message.ordinal(), message.sourceReference(), reason, clock.instant()));
        return new MessageResult(message.ordinal(), message.sourceReference(), review.id(), null, null,
                MessageStatus.NEEDS_REVIEW);
    }

    private String messageFilename(String filename, InboundEdiTranslator.Translation translation) {
        return translation.messageCount() == 1 ? filename : filename + ".message-" + translation.messageOrdinal();
    }

    private String bounded(String value) {
        String safe = value == null || value.isBlank() ? "Inbound message could not be processed" : value;
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }

    private String inboundKey(UUID transferId, int ordinal) {
        return ordinal == 1 ? "mft:" + transferId : "mft:" + transferId + ":message:" + ordinal;
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
    private record Prepared(InboundEdiTranslator.Translation translation, Route route, byte[] canonical) { }
    private record Stored(UUID documentSetId, UUID documentId, UUID sourceVersionId, String sourceSha256,
                          UUID canonicalDerivativeId, String canonicalSha256) { }
    public enum MessageStatus { STORED, NEEDS_REVIEW }
    public record MessageResult(int messageOrdinal, String sourceMessageReference, UUID exchangeId,
                                UUID documentSetId, UUID documentId, MessageStatus status) { }
    public record Result(List<MessageResult> messages, int storedCount, int reviewCount) { }
    private static final class NamedBytes extends ByteArrayResource {
        private final String filename;
        private NamedBytes(byte[] bytes, String filename) { super(bytes); this.filename = filename; }
        @Override public String getFilename() { return filename; }
    }
}
