package com.edispark.mft.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConnectionDetailsServiceTest {
    private static final String TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    void returnsOnlyTheCurrentTenantsRetailerSharingDetails() {
        InMemoryMftRepository repository = new InMemoryMftRepository();
        CertificateRecord identity = identity(TENANT_ID, "EDISPARK-CERTIFICATE");
        PartnerProfile partner = partner(TENANT_ID, identity.id(), "EDISPARK", "Northstar Trading");
        repository.saveCertificate(identity);
        repository.savePartner(partner);
        CertificateRecord otherIdentity = identity("another-tenant", "OTHER-CERTIFICATE");
        repository.saveCertificate(otherIdentity);
        repository.savePartner(partner("another-tenant", otherIdentity.id(), "OTHER", "Other Retailer"));
        ConnectionDetailsService service = new ConnectionDetailsService(
                repository,
                URI.create("https://as2.edispark.example/as2"),
                URI.create("https://as2.edispark.example/as2/mdn"),
                List.of("AES128-GCM", "AES256-GCM"),
                List.of("SHA-256", "SHA-512"),
                "RSA-OAEP");

        AtomicReference<List<ConnectionDetailsService.ConnectionDetails>> result = new AtomicReference<>();
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT_ID)),
                () -> result.set(service.list()));

        assertThat(result.get()).singleElement().satisfies(details -> {
            assertThat(details.partnerId()).isEqualTo(partner.id());
            assertThat(details.partnerName()).isEqualTo("Northstar Trading");
            assertThat(details.as2Identifier()).isEqualTo("EDISPARK");
            assertThat(details.inboundUrl()).isEqualTo(URI.create("https://as2.edispark.example/as2"));
            assertThat(details.asynchronousMdnUrl()).isEqualTo(URI.create("https://as2.edispark.example/as2/mdn"));
            assertThat(details.keyEncryptionAlgorithm()).isEqualTo("RSA-OAEP");
            assertThat(details.publicCertificatePem()).isEqualTo("EDISPARK-CERTIFICATE");
            assertThat(details.certificateFingerprintSha256()).isEqualTo(identity.fingerprintSha256());
        });
    }

    private CertificateRecord identity(String tenantId, String publicCertificatePem) {
        return new CertificateRecord(UUID.randomUUID(), tenantId, "Organisation AS2 certificate",
                CertificateKind.LOCAL_IDENTITY, "AA:BB:CC", "CN=EdiSpark", "CN=EdiSpark", NOW.minusSeconds(60),
                NOW.plusSeconds(86_400), true, publicCertificatePem, "secret-ref", NOW);
    }

    private PartnerProfile partner(String tenantId, UUID certificateId, String localAs2Id, String name) {
        return new PartnerProfile(UUID.randomUUID(), tenantId, name, localAs2Id, "RETAILER",
                URI.create("https://retailer.example/as2"), certificateId, UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false, NOW);
    }
}
