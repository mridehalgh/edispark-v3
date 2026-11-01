package com.edispark.mft.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.domain.model.CertificateRecord;
import com.edispark.mft.domain.repository.SecretStore;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CertificateLifecycleTest {
    private static final String TENANT = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void permanentlyDeletesOnlyUnusedCertificateMaterial() {
        var repository = new InMemoryMftRepository();
        var secrets = new RecordingSecretStore();
        var service = new CertificateService(repository, secrets, Clock.fixed(NOW, ZoneOffset.UTC));
        CertificateRecord certificate = certificate();
        repository.saveCertificate(certificate);

        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () -> {
            assertThat(service.usage(certificate.id()).deletable()).isTrue();
            try { service.deleteUnused(certificate.id()); } catch (IOException exception) { throw new AssertionError(exception); }
            assertThat(repository.findCertificate(TENANT, certificate.id())).isEmpty();
            assertThat(secrets.deleted).isEqualTo(certificate.secretRef());
        });
    }

    @Test
    void reportsMissingCertificatesWithoutCrossTenantFallback() {
        var service = new CertificateService(new InMemoryMftRepository(), new RecordingSecretStore(), Clock.systemUTC());
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(TENANT)), () ->
                assertThatThrownBy(() -> service.usage(UUID.randomUUID())).isInstanceOf(MftNotFoundException.class));
    }

    private CertificateRecord certificate() {
        return new CertificateRecord(UUID.randomUUID(), TENANT, "Unused partner certificate",
                CertificateKind.PARTNER_PUBLIC, "AA:BB", "CN=[name]", "CN=Test CA",
                NOW.minusSeconds(60), NOW.plusSeconds(3600), false,
                "-----BEGIN CERTIFICATE-----\nplaceholder\n-----END CERTIFICATE-----\n", "secret-ref", NOW);
    }

    private static final class RecordingSecretStore implements SecretStore {
        private String deleted;
        @Override public String put(String tenantId, String name, byte[] plaintext) { return "secret-ref"; }
        @Override public byte[] get(String tenantId, String reference) { return new byte[0]; }
        @Override public void delete(String tenantId, String reference) { deleted = reference; }
    }
}
