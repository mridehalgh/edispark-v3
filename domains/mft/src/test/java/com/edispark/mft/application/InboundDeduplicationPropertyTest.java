package com.edispark.mft.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import com.edispark.mft.infrastructure.storage.FileSystemPayloadStore;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.ArrayList;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

class InboundDeduplicationPropertyTest {
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

    /** Feature: mft-as2, Property 2: an AS2 message replay produces one inbound transfer. */
    @Property(tries = 50)
    void replaysReturnTheOriginalTransfer(@ForAll @NotBlank String messageSeed,
                                          @ForAll @Size(min = 1, max = 8_192) byte[] payload) throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloadStore = new FileSystemPayloadStore(Files.createTempDirectory("mft-inbound"));
        UUID partnerId = UUID.nameUUIDFromBytes(messageSeed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        com.edispark.testing.FixtureTenants.persist(new PartnerProfile(partnerId, "2OnHVzeAJKwm0ThFLcy1HfLHGPm", "Partner", "EDISPARK", "SIMULATOR",
                URI.create("http://localhost:8082/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false, Instant.EPOCH), record -> record.tenantId(), repository::savePartner);
        var events = new ArrayList<InboundPayloadReceived>();
        MftService service = new MftService(repository, payloadStore,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), false, false,
                100L * 1024 * 1024, java.time.Duration.ofDays(7), events::add);
        String messageId = "<" + partnerId + "@simulator.local>";

        Transfer first = new com.edispark.mft.infrastructure.as2.MftInboundGateway(repository, service).receiveInbound("EDISPARK", "SIMULATOR", messageId,
                "orders.edi", "application/edifact", payload);
        Transfer replay = new com.edispark.mft.infrastructure.as2.MftInboundGateway(repository, service).receiveInbound("EDISPARK", "SIMULATOR", messageId,
                "orders.edi", "application/edifact", payload);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(io.cottn.core.tenancy.TenantExecution.get("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> repository.listTransfers())).hasSize(1);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(InboundPayloadReceived::eventId).containsOnly(events.getFirst().eventId());
        assertThat(events.getFirst().transferId()).isEqualTo(first.id().value());
    }
}
