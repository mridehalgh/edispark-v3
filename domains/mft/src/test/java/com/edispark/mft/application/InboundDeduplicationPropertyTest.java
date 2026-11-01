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
    /** Feature: mft-as2, Property 2: an AS2 message replay produces one inbound transfer. */
    @Property(tries = 50)
    void replaysReturnTheOriginalTransfer(@ForAll @NotBlank String messageSeed,
                                          @ForAll @Size(min = 1, max = 8_192) byte[] payload) throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloadStore = new FileSystemPayloadStore(Files.createTempDirectory("mft-inbound"));
        UUID partnerId = UUID.nameUUIDFromBytes(messageSeed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        repository.savePartner(new PartnerProfile(partnerId, "tenant-a", "Partner", "EDISPARK", "SIMULATOR",
                URI.create("http://localhost:8082/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false, Instant.EPOCH));
        var events = new ArrayList<InboundPayloadReceived>();
        MftService service = new MftService(repository, payloadStore,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), false, false,
                100L * 1024 * 1024, java.time.Duration.ofDays(7), events::add);
        String messageId = "<" + partnerId + "@simulator.local>";

        Transfer first = service.receiveInbound("EDISPARK", "SIMULATOR", messageId,
                "orders.edi", "application/edifact", payload);
        Transfer replay = service.receiveInbound("EDISPARK", "SIMULATOR", messageId,
                "orders.edi", "application/edifact", payload);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(repository.listTransfers("tenant-a")).hasSize(1);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(InboundPayloadReceived::eventId).containsOnly(events.getFirst().eventId());
        assertThat(events.getFirst().transferId()).isEqualTo(first.id().value());
    }
}
