package com.edispark.mft.infrastructure.seed;

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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMftDemoSeederTest {
    private static final String TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    void repairsMissingTransferScenariosWithoutDuplicatingExistingFixtures() throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloadStore = new FileSystemPayloadStore(Files.createTempDirectory("mft-demo"));
        PartnerProfile partner = partner();
        repository.savePartner(partner);
        repository.save(existingInbound(partner, payloadStore));
        LocalMftDemoSeeder seeder = new LocalMftDemoSeeder(
                repository, payloadStore, null, null, Clock.fixed(NOW, ZoneOffset.UTC));

        seeder.reconcileTransfers(partner);
        seeder.reconcileTransfers(partner);

        assertThat(repository.listTransfers(TENANT_ID)).hasSize(5);
        assertThat(repository.listTransfers(TENANT_ID))
                .extracting(Transfer::status)
                .containsExactlyInAnyOrder(
                        TransferStatus.DELIVERED,
                        TransferStatus.DELIVERED,
                        TransferStatus.AWAITING_MDN,
                        TransferStatus.RETRY_SCHEDULED,
                        TransferStatus.NEEDS_REVIEW);
    }

    private Transfer existingInbound(PartnerProfile partner, FileSystemPayloadStore payloadStore) throws Exception {
        Instant createdAt = NOW.minusSeconds(420);
        Transfer transfer = Transfer.inbound(TENANT_ID, partner.id(), "PO_45001235.edi", "application/edifact",
                LocalMftDemoSeeder.INBOUND_MESSAGE_ID, createdAt);
        transfer.attachPayload(payloadStore.put(TENANT_ID, transfer.id(), transfer.filename(),
                new ByteArrayInputStream("UNH+1+ORDERS:D:96A:UN'".getBytes(StandardCharsets.UTF_8))), createdAt);
        transfer.deliver("automatic-action/MDN-sent-automatically; processed", createdAt.plusSeconds(2));
        return transfer;
    }

    private PartnerProfile partner() {
        return new PartnerProfile(
                UUID.randomUUID(), TENANT_ID, "Northstar Trading", "EDISPARK", "SIMULATOR",
                URI.create("http://localhost:8082/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false, NOW.minusSeconds(600));
    }
}
