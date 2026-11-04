package com.edispark.mft.infrastructure.as2;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.mft.application.As2DeliveryResult;
import com.edispark.mft.application.MftDeliveryService;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferStatus;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import com.edispark.mft.infrastructure.storage.FileSystemPayloadStore;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MftDeliveryWorkerTest {
    private static final String TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @Test
    void failedOutboundDeliveryDoesNotBlockTheNextQueuedTransfer() throws Exception {
        var repository = new InMemoryMftRepository();
        var payloads = new FileSystemPayloadStore(Files.createTempDirectory("mft-delivery-worker"));
        var partner = partner();
        repository.savePartner(partner);
        Transfer bad = transfer(partner, payloads, "bad-export", NOW.minusSeconds(2));
        Transfer good = transfer(partner, payloads, "good-export", NOW.minusSeconds(1));
        repository.save(bad);
        repository.save(good);
        var attempts = new AtomicInteger();
        var deliveries = new MftDeliveryService(repository, payloads, (profile, transfer, payload) -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalArgumentException("bad outbound payload");
            return new As2DeliveryResult(false, "automatic-action/MDN-sent-automatically; processed", null);
        }, Clock.fixed(NOW, ZoneOffset.UTC));
        var worker = new MftDeliveryWorker(repository, deliveries, Clock.fixed(NOW, ZoneOffset.UTC));

        worker.deliverDueTransfers();

        assertThat(repository.findTransfer(TENANT_ID, bad.id()).orElseThrow().status())
                .isEqualTo(TransferStatus.NEEDS_REVIEW);
        assertThat(repository.findTransfer(TENANT_ID, good.id()).orElseThrow().status())
                .isEqualTo(TransferStatus.DELIVERED);
        assertThat(attempts).hasValue(2);
    }

    private Transfer transfer(PartnerProfile partner, FileSystemPayloadStore payloads, String key, Instant createdAt)
            throws Exception {
        Transfer transfer = Transfer.outbound(TENANT_ID, partner.id(), key + ".edi", "application/edifact",
                "<" + key + "@as2.edispark.local>", key, null, createdAt);
        transfer.attachPayload(payloads.put(TENANT_ID, transfer.id(), transfer.filename(),
                new ByteArrayInputStream("UNH+1+ORDERS:D:96A:UN'".getBytes(StandardCharsets.US_ASCII))), createdAt);
        return transfer;
    }

    private PartnerProfile partner() {
        return new PartnerProfile(UUID.randomUUID(), TENANT_ID, "Retailer", "EDISPARK", "RETAILER",
                URI.create("https://retailer.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, false, false, NOW.minusSeconds(60));
    }
}
