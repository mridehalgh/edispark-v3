package com.edispark.mft.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.repository.PayloadStore;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import com.edispark.mft.infrastructure.storage.FileSystemPayloadStore;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MftTenantIsolationSecurityTest {
    private static final String TENANT_A = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final String TENANT_B = "3PnIWafBKLxn1UiGMdz2IgMIRNn";
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void cannotCreatePartnerWithAnotherOrganisationsCertificates() throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloads = new FileSystemPayloadStore(Files.createTempDirectory("mft-isolation"));
        CertificateRecord local = certificate(TENANT_A, CertificateKind.LOCAL_IDENTITY);
        CertificateRecord remote = certificate(TENANT_B, CertificateKind.PARTNER_PUBLIC);
        repository.saveCertificate(local);
        repository.saveCertificate(remote);
        MftService service = new MftService(repository, payloads, CLOCK);

        assertThatThrownBy(() -> inTenant(TENANT_A, () -> service.createPartner(command(local.id(), remote.id(),
                "EDISPARK-A", "RETAILER-A"))))
                .isInstanceOf(MftNotFoundException.class)
                .hasMessage("Partner AS2 certificate was not found");
        assertThat(repository.listPartners(TENANT_A)).isEmpty();
    }

    @Test
    void cannotCreatePartnerWithCertificatesUsedForTheWrongPurpose() throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        CertificateRecord first = certificate(TENANT_A, CertificateKind.PARTNER_PUBLIC);
        CertificateRecord second = certificate(TENANT_A, CertificateKind.PARTNER_PUBLIC);
        repository.saveCertificate(first);
        repository.saveCertificate(second);
        MftService service = new MftService(repository,
                new FileSystemPayloadStore(Files.createTempDirectory("mft-purpose")), CLOCK);

        assertThatThrownBy(() -> inTenant(TENANT_A, () -> service.createPartner(command(first.id(), second.id(),
                "EDISPARK-A", "RETAILER-A"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Organisation AS2 certificate has the wrong purpose");
    }

    @Test
    void oneAs2IdentityPairCannotBeClaimedByTwoOrganisations() throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        MftService service = new MftService(repository,
                new FileSystemPayloadStore(Files.createTempDirectory("mft-pair-owner")), CLOCK);
        CertificateRecord localA = certificate(TENANT_A, CertificateKind.LOCAL_IDENTITY);
        CertificateRecord remoteA = certificate(TENANT_A, CertificateKind.PARTNER_PUBLIC);
        CertificateRecord localB = certificate(TENANT_B, CertificateKind.LOCAL_IDENTITY);
        CertificateRecord remoteB = certificate(TENANT_B, CertificateKind.PARTNER_PUBLIC);
        repository.saveCertificate(localA);
        repository.saveCertificate(remoteA);
        repository.saveCertificate(localB);
        repository.saveCertificate(remoteB);
        inTenant(TENANT_A, () -> service.createPartner(command(localA.id(), remoteA.id(), "EDISPARK", "RETAILER")));

        assertThatThrownBy(() -> inTenant(TENANT_B, () -> service.createPartner(
                command(localB.id(), remoteB.id(), "EDISPARK", "RETAILER"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The AS2 identity pair is already assigned to a workspace");
        assertThat(repository.listPartners(TENANT_B)).isEmpty();
    }

    @Test
    void publicInboundRouteStoresTheFileOnlyInTheOwningOrganisation() throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        FileSystemPayloadStore payloads = new FileSystemPayloadStore(Files.createTempDirectory("mft-inbound-owner"));
        PartnerProfile owner = partner(TENANT_A, "EDISPARK-A", "RETAILER-A");
        PartnerProfile other = partner(TENANT_B, "EDISPARK-B", "RETAILER-B");
        repository.savePartner(owner);
        repository.savePartner(other);
        MftService service = new MftService(repository, payloads, CLOCK);

        Transfer transfer = service.receiveInbound("EDISPARK-A", "RETAILER-A", "<message@retailer>",
                "orders.edi", "application/edifact", "UNA:+.?".getBytes());

        assertThat(transfer.tenantId()).isEqualTo(TENANT_A);
        assertThat(repository.listTransfers(TENANT_A)).containsExactly(transfer);
        assertThat(repository.listTransfers(TENANT_B)).isEmpty();
        assertThat(payloads.get(TENANT_A, transfer.payload()).readAllBytes()).isEqualTo("UNA:+.?".getBytes());
        assertThatThrownBy(() -> payloads.get(TENANT_B, transfer.payload()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void outboundSubmissionCannotUseAnotherOrganisationsPartnerAndWritesNoPayload() {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        PartnerProfile partnerB = partner(TENANT_B, "EDISPARK-B", "RETAILER-B");
        repository.savePartner(partnerB);
        RecordingPayloadStore payloads = new RecordingPayloadStore();
        MftService service = new MftService(repository, payloads, CLOCK);

        assertThatThrownBy(() -> inTenant(TENANT_A, () -> service.submitOutbound(new MftService.SubmitOutbound(
                partnerB.id(), "idempotency", "invoice.edi", "application/edifact",
                new ByteArrayInputStream(new byte[]{1, 2, 3}), null))))
                .isInstanceOf(MftNotFoundException.class)
                .hasMessage("Partner was not found");
        assertThat(payloads.putCalls).isZero();
        assertThat(repository.listTransfers(TENANT_A)).isEmpty();
        assertThat(repository.listTransfers(TENANT_B)).isEmpty();
    }

    @Test
    void idempotencyKeysAreScopedToOneOrganisation() {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        Transfer first = Transfer.outbound(TENANT_A, UUID.randomUUID(), "a.edi", "application/edifact",
                "<a@example>", "same-key", null, NOW);
        Transfer second = Transfer.outbound(TENANT_B, UUID.randomUUID(), "b.edi", "application/edifact",
                "<b@example>", "same-key", null, NOW);

        assertThat(repository.saveOutboundIfAbsent(first)).isSameAs(first);
        assertThat(repository.saveOutboundIfAbsent(second)).isSameAs(second);
        assertThat(repository.listTransfers(TENANT_A)).containsExactly(first);
        assertThat(repository.listTransfers(TENANT_B)).containsExactly(second);
    }

    @Test
    void unknownAs2PairDoesNotFallBackToAnyOrganisation() throws Exception {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        repository.savePartner(partner(TENANT_A, "EDISPARK-A", "RETAILER-A"));
        MftService service = new MftService(repository,
                new FileSystemPayloadStore(Files.createTempDirectory("mft-unknown-pair")), CLOCK);

        assertThatThrownBy(() -> service.receiveInbound("EDISPARK-A", "ATTACKER", "<unknown@example>",
                "payload.edi", "application/edifact", new byte[]{1}))
                .isInstanceOf(MftNotFoundException.class)
                .hasMessage("Inbound AS2 partnership was not found");
        assertThat(repository.listTransfers(TENANT_A)).isEmpty();
    }

    private MftService.CreatePartner command(UUID local, UUID remote, String localAs2, String remoteAs2) {
        return new MftService.CreatePartner("Retailer", localAs2, remoteAs2,
                URI.create("https://retailer.example/as2"), local, remote, MdnMode.SYNCHRONOUS,
                true, true, true, false);
    }

    private PartnerProfile partner(String tenantId, String localAs2, String remoteAs2) {
        return new PartnerProfile(UUID.randomUUID(), tenantId, "Retailer", localAs2, remoteAs2,
                URI.create("https://retailer.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false, NOW);
    }

    private CertificateRecord certificate(String tenantId, CertificateKind kind) {
        return new CertificateRecord(UUID.randomUUID(), tenantId, kind.name(), kind, UUID.randomUUID().toString(),
                "CN=[name]", "CN=[name]", NOW.minusSeconds(60), NOW.plusSeconds(86_400),
                kind == CertificateKind.LOCAL_IDENTITY, "-----BEGIN CERTIFICATE-----\nTEST\n-----END CERTIFICATE-----\n",
                "secret-ref", NOW);
    }

    private void inTenant(String tenantId, ThrowingAction action) {
        AtomicReference<Exception> failure = new AtomicReference<>();
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), () -> {
            try {
                action.run();
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() instanceof RuntimeException runtime) throw runtime;
        if (failure.get() != null) throw new IllegalStateException(failure.get());
    }

    @FunctionalInterface
    private interface ThrowingAction { void run() throws Exception; }

    private static final class RecordingPayloadStore implements PayloadStore {
        private int putCalls;
        @Override public PayloadRef put(String tenantId, TransferId transferId, String filename, InputStream content) {
            putCalls++;
            return new PayloadRef(tenantId + "/" + transferId, "00", 0);
        }
        @Override public InputStream get(String tenantId, PayloadRef payload) throws IOException {
            return InputStream.nullInputStream();
        }
    }
}
