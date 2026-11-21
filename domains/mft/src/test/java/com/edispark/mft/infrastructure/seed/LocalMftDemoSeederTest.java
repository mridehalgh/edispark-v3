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

    private static final String TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    void repairsMissingTransferScenariosWithoutDuplicatingExistingFixtures() throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloadStore = new FileSystemPayloadStore(Files.createTempDirectory("mft-demo"));
        PartnerProfile partner = partner();
        com.edispark.testing.FixtureTenants.persist(partner, record -> record.tenantId(), repository::savePartner);
        com.edispark.testing.FixtureTenants.persist(existingInbound(partner, payloadStore), record -> record.tenantId(), repository::save);
        LocalMftDemoSeeder seeder = new LocalMftDemoSeeder(
                repository, payloadStore, null, null, Clock.fixed(NOW, ZoneOffset.UTC));

        seeder.reconcileTransfers(partner);
        seeder.reconcileTransfers(partner);
        seeder.reconcileLifecycleTransfers(partner, false);
        seeder.reconcileLifecycleTransfers(partner, false);
        seeder.reconcilePartnerRevision(partner);
        seeder.reconcilePartnerRevision(partner);

        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listTransfers())).hasSize(9);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listPartners())).hasSize(2);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listTransfers()))
                .extracting(Transfer::status)
                .containsExactlyInAnyOrder(
                        TransferStatus.DELIVERED,
                        TransferStatus.DELIVERED,
                        TransferStatus.AWAITING_MDN,
                        TransferStatus.RETRY_SCHEDULED,
                        TransferStatus.NEEDS_REVIEW,
                        TransferStatus.DELIVERED,
                        TransferStatus.DELIVERED,
                        TransferStatus.DELIVERED,
                        TransferStatus.DELIVERED);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listTransfers())).allSatisfy(transfer -> {
            try {
                String edi = new String(payloadStore.get(TENANT_ID, transfer.payload()).readAllBytes(),
                        StandardCharsets.UTF_8);
                assertThat(edi).startsWith("UNB+").contains("UNH+1+").contains("UNT+").contains("UNZ+1+");
                assertThat(edi.lines()).hasSizeGreaterThanOrEqualTo(10);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listTransfers())).filteredOn(transfer ->
                transfer.filename().endsWith("_2026_0042.edi")).hasSize(4)
                .extracting(Transfer::direction)
                .containsExactlyInAnyOrder(
                        com.edispark.mft.domain.model.TransferDirection.OUTBOUND,
                        com.edispark.mft.domain.model.TransferDirection.OUTBOUND,
                        com.edispark.mft.domain.model.TransferDirection.INBOUND,
                        com.edispark.mft.domain.model.TransferDirection.INBOUND);
    }

    @Test
    void createsOneReceivedOrderAndOneSentInvoiceForTradacomsRetailer() throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloadStore = new FileSystemPayloadStore(Files.createTempDirectory("mft-tradacoms-demo"));
        PartnerProfile partner = partner("Harbour Retail", "SIMULATOR_TRADACOMS");
        com.edispark.testing.FixtureTenants.persist(partner, record -> record.tenantId(), repository::savePartner);
        LocalMftDemoSeeder seeder = new LocalMftDemoSeeder(
                repository, payloadStore, null, null, Clock.fixed(NOW, ZoneOffset.UTC));

        seeder.reconcileTradacomsTransfers(partner);
        seeder.reconcileTradacomsTransfers(partner);
        seeder.reconcileLifecycleTransfers(partner, true);
        seeder.reconcileLifecycleTransfers(partner, true);

        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listTransfers())).hasSize(6);
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listTransfers())).allSatisfy(transfer -> {
            try {
                String edi = new String(payloadStore.get(TENANT_ID, transfer.payload()).readAllBytes(),
                        StandardCharsets.UTF_8);
                assertThat(edi).startsWith("STX=").contains("MTR=").contains("END=");
                if (transfer.filename().startsWith("PO_HARBOUR")) {
                    assertThat(edi).contains("MHD=1+ORDERS:9'").contains("END=1'");
                    assertThat(edi.split("MHD=", -1)).hasSize(2);
                } else {
                    assertThat(edi).contains("MHD=2+").contains("END=2'");
                }
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listTransfers())).anySatisfy(transfer -> {
            assertThat(transfer.direction()).isEqualTo(com.edispark.mft.domain.model.TransferDirection.INBOUND);
            assertThat(transfer.filename()).startsWith("PO_HARBOUR");
        }).anySatisfy(transfer -> {
            assertThat(transfer.direction()).isEqualTo(com.edispark.mft.domain.model.TransferDirection.OUTBOUND);
            assertThat(transfer.filename()).startsWith("invoice_harbour");
        });
        assertThat(io.cottn.core.tenancy.TenantExecution.get(TENANT_ID, () -> repository.listTransfers())).filteredOn(transfer ->
                transfer.filename().contains("harbour_2026_0042.edi")).hasSize(4)
                .extracting(Transfer::direction)
                .containsExactlyInAnyOrder(
                        com.edispark.mft.domain.model.TransferDirection.OUTBOUND,
                        com.edispark.mft.domain.model.TransferDirection.OUTBOUND,
                        com.edispark.mft.domain.model.TransferDirection.INBOUND,
                        com.edispark.mft.domain.model.TransferDirection.INBOUND);
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
        return partner("Northstar Retail", "SIMULATOR");
    }

    private PartnerProfile partner(String name, String remoteAs2Id) {
        return new PartnerProfile(
                UUID.randomUUID(), TENANT_ID, name, "EDISPARK", remoteAs2Id,
                URI.create("http://localhost:8082/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false, NOW.minusSeconds(600));
    }
}
