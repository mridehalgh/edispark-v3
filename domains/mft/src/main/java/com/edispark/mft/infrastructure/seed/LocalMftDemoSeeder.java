package com.edispark.mft.infrastructure.seed;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.mft.application.CertificateService;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferDirection;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.PayloadStore;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("local & !openapi-export")
@ConditionalOnProperty(name = "mft.local-demo.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public final class LocalMftDemoSeeder {
    private static final java.util.UUID DRAFT_PARTNER_ID = java.util.UUID.fromString("850ca900-3f1e-4f98-b5e2-f17f557cc9e1");
    private static final String CONTENT_TYPE = "application/edifact";
    static final String INBOUND_MESSAGE_ID = "<local-inbound@northstar.test>";
    static final String TRADACOMS_INBOUND_MESSAGE_ID = "<local-inbound@harbour.test>";
    static final String DELIVERED_KEY = "demo-delivered";
    static final String TRADACOMS_DELIVERED_KEY = "demo-tradacoms-delivered";
    static final String AWAITING_MDN_KEY = "demo-awaiting-mdn";
    static final String RETRY_KEY = "demo-retry";
    static final String REVIEW_KEY = "demo-review";

    private final MftRepository repository;
    private final PayloadStore payloadStore;
    private final CertificateService certificateService;
    private final MftService mftService;
    private final Clock clock;

    @Value("${mft.local-demo.tenant-id}")
    private String tenantId;
    @Value("${mft.local-demo.identity-path}")
    private Path identityPath;
    @Value("${mft.local-demo.partner-certificate-path}")
    private Path partnerCertificatePath;
    @Value("${mft.local-demo.password-file}")
    private Path passwordFile;
    @Value("${mft.local-demo.partner-endpoint}")
    private URI partnerEndpoint;
    @Value("${mft.local-demo.partner-id-file}")
    private Path partnerIdFile;
    @Value("${mft.local-demo.tradacoms-partner-id-file}")
    private Path tradacomsPartnerIdFile;

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void seed() {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), this::seedTenantData);
    }

    void seedTenantData() {
        try {
            PartnerProfile edifactPartner = findDemoPartner("SIMULATOR");
            if (edifactPartner == null) edifactPartner = createEdifactPartner();
            PartnerProfile tradacomsPartner = findDemoPartner("SIMULATOR_TRADACOMS");
            if (tradacomsPartner == null) tradacomsPartner = createTradacomsPartner(edifactPartner);
            reconcilePartnerRevision(edifactPartner);
            writePartnerId(partnerIdFile, edifactPartner);
            writePartnerId(tradacomsPartnerIdFile, tradacomsPartner);
            reconcileTransfers(edifactPartner);
            reconcileTradacomsTransfers(tradacomsPartner);
            log.info("MFT demo ready for tenant {}; workspace totals: {} partner(s), {} certificate(s), {} transfer(s)",
                    tenantId,
                    repository.listPartners(tenantId).size(), repository.listCertificates(tenantId).size(),
                    repository.listTransfers(tenantId).size());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to seed the local MFT demo", exception);
        }
    }

    void reconcilePartnerRevision(PartnerProfile active) {
        boolean exists = repository.listPartners(active.tenantId()).stream()
                .anyMatch(item -> item.id().equals(DRAFT_PARTNER_ID));
        if (exists) return;
        repository.savePartner(new PartnerProfile(DRAFT_PARTNER_ID, active.tenantId(), active.name(),
                active.localAs2Id(), active.remoteAs2Id(), active.endpoint(), active.localCertificateId(),
                active.partnerSigningCertificateId(), active.partnerEncryptionCertificateId(),
                active.previousPartnerSigningCertificateId(), active.signingCertificateGraceUntil(),
                active.asynchronousMdnEndpoint(), active.mdnMode(), active.signingRequired(),
                active.encryptionRequired(), active.compressionEnabled(), active.legacyCryptoEnabled(),
                com.edispark.mft.domain.model.PartnerStatus.DRAFT, clock.instant(), active.familyId(),
                active.revision() + 1, null, null, clock.instant(), 1));
    }

    private void writePartnerId(Path path, PartnerProfile partner) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, partner.id().toString(), StandardCharsets.US_ASCII);
    }

    private PartnerProfile findDemoPartner(String remoteAs2Id) {
        return repository.listPartners(tenantId).stream()
                .filter(partner -> partner.localAs2Id().equals("EDISPARK"))
                .filter(partner -> partner.remoteAs2Id().equals(remoteAs2Id))
                .filter(partner -> partner.status() == com.edispark.mft.domain.model.PartnerStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    private PartnerProfile createEdifactPartner() throws IOException {
        char[] password = Files.readString(passwordFile).trim().toCharArray();
        try {
            CertificateRecord identity = certificateService.upload("EdiSpark local KMS identity",
                    CertificateKind.LOCAL_IDENTITY, Files.readAllBytes(identityPath), password);
            CertificateRecord partnerCertificate = certificateService.upload("Local retailer public certificate",
                    CertificateKind.PARTNER_PUBLIC, Files.readAllBytes(partnerCertificatePath), new char[0]);
            return mftService.createPartner(new MftService.CreatePartner(
                    "Northstar Retail", "EDISPARK", "SIMULATOR", partnerEndpoint,
                    identity.id(), partnerCertificate.id(), MdnMode.SYNCHRONOUS,
                    true, true, true, false));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private PartnerProfile createTradacomsPartner(PartnerProfile edifactPartner) {
        return mftService.createPartner(new MftService.CreatePartner(
                "Harbour Retail", "EDISPARK", "SIMULATOR_TRADACOMS", partnerEndpoint,
                edifactPartner.localCertificateId(), edifactPartner.partnerSigningCertificateId(),
                MdnMode.SYNCHRONOUS, true, true, true, false));
    }

    void reconcileTransfers(PartnerProfile partner) throws IOException {
        var existing = repository.listTransfers(partner.tenantId());
        Instant now = clock.instant();
        if (!hasInbound(existing)) {
            Transfer inbound = inbound(partner, "PO_45001235.edi", INBOUND_MESSAGE_ID, now.minusSeconds(420));
            inbound.deliver("automatic-action/MDN-sent-automatically; processed", now.minusSeconds(418));
            repository.save(inbound);
        }
        if (!hasOutbound(existing, DELIVERED_KEY, TransferStatus.DELIVERED)) {
            Transfer delivered = outbound(partner, "invoice_77891.edi", DELIVERED_KEY, now.minusSeconds(300));
            delivered.startAttempt(now.minusSeconds(299));
            delivered.deliver("automatic-action/MDN-sent-automatically; processed", now.minusSeconds(297));
            repository.save(delivered);
        }
        if (!hasOutbound(existing, AWAITING_MDN_KEY, TransferStatus.AWAITING_MDN)) {
            Transfer awaitingMdn = outbound(partner, "ASN_9921.edi", AWAITING_MDN_KEY, now.minusSeconds(120));
            awaitingMdn.startAttempt(now.minusSeconds(119));
            awaitingMdn.awaitMdn(now.minusSeconds(118));
            repository.save(awaitingMdn);
        }
        if (!hasOutbound(existing, RETRY_KEY, TransferStatus.RETRY_SCHEDULED)) {
            Transfer retry = outbound(partner, "inventory_20261030.edi", RETRY_KEY, now.minusSeconds(90));
            retry.startAttempt(now.minusSeconds(89));
            retry.scheduleRetry("Partner endpoint returned a transient response", now.plus(Duration.ofHours(24)),
                    now.minusSeconds(88));
            repository.save(retry);
        }
        if (!hasOutbound(existing, REVIEW_KEY, TransferStatus.NEEDS_REVIEW)) {
            Transfer review = outbound(partner, "remittance_1042.edi", REVIEW_KEY, now.minusSeconds(60));
            review.requireReview("Partner certificate rollover requires confirmation", now.minusSeconds(59));
            repository.save(review);
        }
        reconcileFixturePayloads(partner.tenantId());
    }

    void reconcileTradacomsTransfers(PartnerProfile partner) throws IOException {
        var existing = repository.listTransfers(partner.tenantId());
        Instant now = clock.instant();
        boolean inboundExists = existing.stream().anyMatch(transfer ->
                transfer.direction() == TransferDirection.INBOUND
                        && transfer.as2MessageId().equals(TRADACOMS_INBOUND_MESSAGE_ID));
        if (!inboundExists) {
            Transfer inbound = inbound(partner, "PO_HARBOUR_2026_1187.edi", TRADACOMS_INBOUND_MESSAGE_ID,
                    now.minusSeconds(360));
            inbound.deliver("automatic-action/MDN-sent-automatically; processed", now.minusSeconds(358));
            repository.save(inbound);
        }
        if (!hasOutbound(existing, TRADACOMS_DELIVERED_KEY, TransferStatus.DELIVERED)) {
            Transfer invoice = outbound(partner, "invoice_harbour_2026_1187.edi", TRADACOMS_DELIVERED_KEY,
                    now.minusSeconds(240));
            invoice.startAttempt(now.minusSeconds(239));
            invoice.deliver("automatic-action/MDN-sent-automatically; processed", now.minusSeconds(237));
            repository.save(invoice);
        }
        reconcileFixturePayloads(partner.tenantId());
    }

    private boolean hasInbound(List<Transfer> transfers) {
        return transfers.stream()
                .anyMatch(transfer -> transfer.direction() == TransferDirection.INBOUND
                        && transfer.as2MessageId().equals(INBOUND_MESSAGE_ID)
                        && transfer.status() == TransferStatus.DELIVERED);
    }

    private boolean hasOutbound(List<Transfer> transfers, String idempotencyKey, TransferStatus status) {
        return transfers.stream()
                .anyMatch(transfer -> transfer.direction() == TransferDirection.OUTBOUND
                        && idempotencyKey.equals(transfer.idempotencyKey())
                        && transfer.status() == status);
    }

    private Transfer inbound(PartnerProfile partner, String filename, String messageId, Instant createdAt)
            throws IOException {
        Transfer transfer = Transfer.inbound(partner.tenantId(), partner.id(), filename, CONTENT_TYPE, messageId,
                createdAt);
        attachPayload(transfer, filename, samplePayload(filename));
        return transfer;
    }

    private Transfer outbound(PartnerProfile partner, String filename, String idempotencyKey, Instant createdAt)
            throws IOException {
        Transfer transfer = Transfer.outbound(partner.tenantId(), partner.id(), filename, CONTENT_TYPE,
                "<" + idempotencyKey + "@as2.edispark.local>", idempotencyKey, null, createdAt);
        attachPayload(transfer, filename, samplePayload(filename));
        return transfer;
    }

    private void attachPayload(Transfer transfer, String filename, byte[] content) throws IOException {
        transfer.attachPayload(payloadStore.put(transfer.tenantId(), transfer.id(), filename,
                new ByteArrayInputStream(content)), transfer.createdAt());
    }

    private void reconcileFixturePayloads(String targetTenantId) throws IOException {
        for (Transfer transfer : repository.listTransfers(targetTenantId)) {
            if (!isDemoFilename(transfer.filename())) continue;
            byte[] expected = samplePayload(transfer.filename());
            byte[] current = transfer.payload() == null ? new byte[0]
                    : payloadStore.get(targetTenantId, transfer.payload()).readAllBytes();
            if (Arrays.equals(current, expected)) continue;
            var payload = payloadStore.put(targetTenantId, transfer.id(), transfer.filename(),
                    new ByteArrayInputStream(expected));
            repository.save(Transfer.restore(transfer.id(), transfer.tenantId(), transfer.partnerId(),
                    transfer.direction(), transfer.filename(), transfer.contentType(), transfer.as2MessageId(),
                    transfer.idempotencyKey(), transfer.resendOf(), transfer.provenance(), transfer.status(),
                    transfer.createdAt(), transfer.updatedAt(), transfer.nextAttemptAt(), payload,
                    transfer.mdnDisposition(), transfer.failureReason(), transfer.sentContentMic(), transfer.attempts()));
        }
    }

    private boolean isDemoFilename(String filename) {
        return filename.equals("PO_45001235.edi") || filename.equals("invoice_77891.edi")
                || filename.equals("ASN_9921.edi") || filename.equals("inventory_20261030.edi")
                || filename.equals("remittance_1042.edi") || filename.equals("PO_HARBOUR_2026_1187.edi")
                || filename.equals("invoice_harbour_2026_1187.edi");
    }

    private byte[] samplePayload(String filename) {
        String resourceName = switch (filename) {
            case "PO_45001235.edi" -> "edifact-orders-d96a.edi";
            case "invoice_77891.edi" -> "edifact-invoic-d96a.edi";
            case "ASN_9921.edi" -> "edifact-desadv-d96a.edi";
            case "inventory_20261030.edi" -> "edifact-invrpt-d96a.edi";
            case "remittance_1042.edi" -> "edifact-remadv-d96a.edi";
            case "PO_HARBOUR_2026_1187.edi" -> "tradacoms-orders-9.edi";
            case "invoice_harbour_2026_1187.edi" -> "tradacoms-invoic-9.edi";
            default -> throw new IllegalArgumentException("No local MFT sample for " + filename);
        };
        try (InputStream input = LocalMftDemoSeeder.class.getResourceAsStream("/seed/mft/" + resourceName)) {
            if (input == null) throw new IllegalStateException("MFT sample resource is missing: " + resourceName);
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read MFT sample resource: " + resourceName, exception);
        }
    }
}
