package com.edispark.mft.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import com.edispark.mft.infrastructure.storage.FileSystemPayloadStore;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsyncMdnOutboxServiceTest {
    @Test
    void queuesOneTenantEncryptedReceiptBeforeReturningForExactRetries() throws Exception {
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloads = new FileSystemPayloadStore(Files.createTempDirectory("mdn-outbox"));
        AsyncMdnOutboxService service = new AsyncMdnOutboxService(repository, payloads,
                Clock.fixed(now, ZoneOffset.UTC));
        PartnerProfile partner = new PartnerProfile(UUID.randomUUID(), "tenant-a", "Retailer", "EDISPARK",
                "RETAILER", URI.create("https://retailer.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.ASYNCHRONOUS, true, true, false, false, now);
        URI callback = URI.create("https://retailer.example/as2/mdn");
        byte[] signedReceipt = "signed-mdn".getBytes();

        var first = service.enqueue(partner, callback, "multipart/signed", signedReceipt,
                "<mdn@edispark>", "<original@retailer>");
        var retry = service.enqueue(partner, callback, "multipart/signed", signedReceipt,
                "<different-mdn@edispark>", "<original@retailer>");

        assertThat(retry).isEqualTo(first);
        assertThat(repository.listPendingMdnDeliveries(now, 10)).containsExactly(first);
        assertThat(repository.listMdnDeliveries("tenant-a")).containsExactly(first);
        assertThat(repository.listMdnDeliveries("tenant-b")).isEmpty();
        assertThat(repository.saveMdnDeliveryIfAttempts(first.delivered(now.plusSeconds(1)), 1)).isFalse();
        assertThat(repository.saveMdnDeliveryIfAttempts(first.delivered(now.plusSeconds(1)), 0)).isTrue();
        try (var content = payloads.get("tenant-a", first.payload())) {
            assertThat(content.readAllBytes()).isEqualTo(signedReceipt);
        }
    }
}
