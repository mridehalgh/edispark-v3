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
    @Test void retriesATransportTimeoutWithoutDuplicatingTheTransferOrBlockingItsNeighbour() throws Exception {
        var repository = new InMemoryMftRepository();
        var payloads = new FileSystemPayloadStore(Files.createTempDirectory("mft-timeout-retry"));
        var partner = partner(); com.edispark.testing.FixtureTenants.persist(partner, record -> record.tenantId(), repository::savePartner);
        var timedOut = transfer(partner, payloads, "timeout-export", NOW.minusSeconds(2));
        var neighbour = transfer(partner, payloads, "neighbour-export", NOW.minusSeconds(1));
        com.edispark.testing.FixtureTenants.persist(timedOut, record -> record.tenantId(), repository::save); com.edispark.testing.FixtureTenants.persist(neighbour, record -> record.tenantId(), repository::save);
        var now = new java.util.concurrent.atomic.AtomicReference<>(NOW);
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        var attempts = new AtomicInteger();
        var deliveries = new MftDeliveryService(repository, payloads, (profile, current, bytes) -> {
            if (current.id().equals(timedOut.id()) && attempts.getAndIncrement() == 0)
                throw new com.edispark.mft.application.As2TransportException("AS2 request timed out",
                        new java.net.SocketTimeoutException("local test timeout"), true);
            return new As2DeliveryResult(false, "automatic-action/MDN-sent-automatically; processed", null);
        }, clock);
        var worker = new MftDeliveryWorker(repository, deliveries, clock);
        worker.deliverDueTransfers();
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.findTransfer(timedOut.id())).orElseThrow().status()).isEqualTo(TransferStatus.RETRY_SCHEDULED);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.findTransfer(neighbour.id())).orElseThrow().status()).isEqualTo(TransferStatus.DELIVERED);
        now.set(NOW.plusSeconds(600));
        worker.deliverDueTransfers();
        var recovered = io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.findTransfer(timedOut.id())).orElseThrow();
        assertThat(recovered.status()).isEqualTo(TransferStatus.DELIVERED);
        assertThat(recovered.attempts()).hasSize(2);
        assertThat(recovered.payload()).isEqualTo(timedOut.payload());
        assertThat(attempts).hasValue(2);
        worker.deliverDueTransfers();
        assertThat(attempts).hasValue(2);
    }
    private static final String TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @Test
    void failedOutboundDeliveryDoesNotBlockTheNextQueuedTransfer() throws Exception {
        var repository = new InMemoryMftRepository();
        var payloads = new FileSystemPayloadStore(Files.createTempDirectory("mft-delivery-worker"));
        var partner = partner();
        com.edispark.testing.FixtureTenants.persist(partner, record -> record.tenantId(), repository::savePartner);
        Transfer bad = transfer(partner, payloads, "bad-export", NOW.minusSeconds(2));
        Transfer good = transfer(partner, payloads, "good-export", NOW.minusSeconds(1));
        com.edispark.testing.FixtureTenants.persist(bad, record -> record.tenantId(), repository::save);
        com.edispark.testing.FixtureTenants.persist(good, record -> record.tenantId(), repository::save);
        var attempts = new AtomicInteger();
        var deliveries = new MftDeliveryService(repository, payloads, (profile, transfer, payload) -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalArgumentException("bad outbound payload");
            return new As2DeliveryResult(false, "automatic-action/MDN-sent-automatically; processed", null);
        }, Clock.fixed(NOW, ZoneOffset.UTC));
        var worker = new MftDeliveryWorker(repository, deliveries, Clock.fixed(NOW, ZoneOffset.UTC));

        worker.deliverDueTransfers();

        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.findTransfer(bad.id())).orElseThrow().status())
                .isEqualTo(TransferStatus.NEEDS_REVIEW);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.findTransfer(good.id())).orElseThrow().status())
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
