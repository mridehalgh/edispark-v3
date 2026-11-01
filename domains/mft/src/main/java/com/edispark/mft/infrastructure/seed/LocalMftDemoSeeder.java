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
    private static final String CONTENT_TYPE = "application/edifact";
    static final String INBOUND_MESSAGE_ID = "<local-inbound@northstar.test>";
    static final String DELIVERED_KEY = "demo-delivered";
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

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void seed() {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), this::seedTenantData);
    }

    void seedTenantData() {
        try {
            PartnerProfile partner = findDemoPartner();
            if (partner == null) {
                partner = createPartner();
            }
            writePartnerId(partner);
            reconcileTransfers(partner);
            log.info("MFT demo ready for tenant {}; workspace totals: {} partner(s), {} certificate(s), {} transfer(s)",
                    tenantId,
                    repository.listPartners(tenantId).size(), repository.listCertificates(tenantId).size(),
                    repository.listTransfers(tenantId).size());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to seed the local MFT demo", exception);
        }
    }

    private void writePartnerId(PartnerProfile partner) throws IOException {
        Path parent = partnerIdFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(partnerIdFile, partner.id().toString(), StandardCharsets.US_ASCII);
    }

    private PartnerProfile findDemoPartner() {
        return repository.listPartners(tenantId).stream()
                .filter(partner -> partner.localAs2Id().equals("EDISPARK"))
                .filter(partner -> partner.remoteAs2Id().equals("SIMULATOR"))
                .findFirst()
                .orElse(null);
    }

    private PartnerProfile createPartner() throws IOException {
        char[] password = Files.readString(passwordFile).trim().toCharArray();
        try {
            CertificateRecord identity = certificateService.upload("EdiSpark local KMS identity",
                    CertificateKind.LOCAL_IDENTITY, Files.readAllBytes(identityPath), password);
            CertificateRecord partnerCertificate = certificateService.upload("Northstar Trading public certificate",
                    CertificateKind.PARTNER_PUBLIC, Files.readAllBytes(partnerCertificatePath), new char[0]);
            return mftService.createPartner(new MftService.CreatePartner(
                    "Northstar Trading", "EDISPARK", "SIMULATOR", partnerEndpoint,
                    identity.id(), partnerCertificate.id(), MdnMode.SYNCHRONOUS,
                    true, true, true, false));
        } finally {
            Arrays.fill(password, '\0');
        }
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
        attachPayload(transfer, filename, samplePayload("ORDERS", "PO-45001235"));
        return transfer;
    }

    private Transfer outbound(PartnerProfile partner, String filename, String idempotencyKey, Instant createdAt)
            throws IOException {
        Transfer transfer = Transfer.outbound(partner.tenantId(), partner.id(), filename, CONTENT_TYPE,
                "<" + idempotencyKey + "@as2.edispark.local>", idempotencyKey, null, createdAt);
        attachPayload(transfer, filename, samplePayload("INVOIC", idempotencyKey));
        return transfer;
    }

    private void attachPayload(Transfer transfer, String filename, byte[] content) throws IOException {
        transfer.attachPayload(payloadStore.put(transfer.tenantId(), transfer.id(), filename,
                new ByteArrayInputStream(content)), transfer.createdAt());
    }

    private byte[] samplePayload(String type, String reference) {
        String payload = "UNH+1+" + type + ":D:96A:UN'BGM+220+" + reference
                + "+9'NAD+BY+[buyer_id]++[name]+[address]'UNT+4+1'";
        return payload.getBytes(StandardCharsets.UTF_8);
    }
}
