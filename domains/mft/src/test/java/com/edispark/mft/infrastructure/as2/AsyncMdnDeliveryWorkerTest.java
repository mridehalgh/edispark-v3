package com.edispark.mft.infrastructure.as2;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.mft.application.As2TransportException;
import com.edispark.mft.application.AsyncMdnOutboxService;
import com.edispark.mft.domain.model.MdnDeliveryStatus;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import com.edispark.mft.infrastructure.storage.FileSystemPayloadStore;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncMdnDeliveryWorkerTest {
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
    void retriesTransientFailureAndThenRecordsDelivery() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-01T12:00:00Z"));
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloads = new FileSystemPayloadStore(Files.createTempDirectory("mdn-worker"));
        PartnerProfile partner = new PartnerProfile(UUID.randomUUID(), "2OnHVzeAJKwm0ThFLcy1HfLHGPm", "Retailer", "EDISPARK",
                "RETAILER", URI.create("https://retailer.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.ASYNCHRONOUS, true, true, false, false, clock.instant());
        new AsyncMdnOutboxService(repository, payloads, clock).enqueue(partner,
                URI.create("https://retailer.example/as2/mdn"), "multipart/signed", new byte[]{1, 2, 3},
                "<mdn@edispark>", "<original@retailer>");
        AtomicInteger calls = new AtomicInteger();
        AsyncMdnDeliveryWorker worker = new AsyncMdnDeliveryWorker(repository, payloads,
                new As2EndpointPolicy(true), clock, (delivery, body) -> {
                    if (calls.getAndIncrement() == 0) {
                        throw new As2TransportException("temporary failure", null, true);
                    }
                });

        worker.deliverDue();
        var pending = io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.listMdnDeliveries()).getFirst();
        assertThat(pending.status()).isEqualTo(MdnDeliveryStatus.PENDING);
        assertThat(pending.attempts()).isEqualTo(1);

        clock.advanceSeconds(6);
        worker.deliverDue();
        var delivered = io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.listMdnDeliveries()).getFirst();
        assertThat(delivered.status()).isEqualTo(MdnDeliveryStatus.DELIVERED);
        assertThat(delivered.attempts()).isEqualTo(2);
        assertThat(calls).hasValue(2);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
