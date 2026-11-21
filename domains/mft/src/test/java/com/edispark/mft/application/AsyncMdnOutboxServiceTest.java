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
    private io.cottn.core.tenancy.TenantContext.Scope fixtureScope;

    @org.junit.jupiter.api.BeforeEach
    @net.jqwik.api.lifecycle.BeforeTry
    void bindFixtureTenant() {
        fixtureScope = io.cottn.core.tenancy.TenantContext.where(io.cottn.core.tenancy.TenantContextHolder.of(
                com.edispark.identifiers.tenant.TenantId.fromString("2OnHVzeAJKwm0ThFLcy1HfLHGPm")));
    }

    @org.junit.jupiter.api.AfterEach
    @net.jqwik.api.lifecycle.AfterTry
    void restoreFixtureTenant() {
        if (fixtureScope != null) fixtureScope.close();
    }

    @Test
    void queuesOneTenantEncryptedReceiptBeforeReturningForExactRetries() throws Exception {
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloads = new FileSystemPayloadStore(Files.createTempDirectory("mdn-outbox"));
        AsyncMdnOutboxService service = new AsyncMdnOutboxService(repository, payloads,
                Clock.fixed(now, ZoneOffset.UTC));
        PartnerProfile partner = new PartnerProfile(UUID.randomUUID(), "2OnHVzeAJKwm0ThFLcy1HfLHGPm", "Retailer", "EDISPARK",
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
        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.listMdnDeliveries())).containsExactly(first);
        assertThat(io.cottn.core.tenancy.TenantExecution.get("3PnIWafBKLxn1UiGMdz2IgMIRNn", () -> repository.listMdnDeliveries())).isEmpty();
        assertThat(repository.saveMdnDeliveryIfAttempts(first.delivered(now.plusSeconds(1)), 1)).isFalse();
        assertThat(repository.saveMdnDeliveryIfAttempts(first.delivered(now.plusSeconds(1)), 0)).isTrue();
        try (var content = payloads.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", first.payload())) {
            assertThat(content.readAllBytes()).isEqualTo(signedReceipt);
        }
    }
}
