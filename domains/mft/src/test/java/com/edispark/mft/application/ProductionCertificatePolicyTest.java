package com.edispark.mft.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.mft.domain.model.CertificateKind;
import com.edispark.mft.infrastructure.persistence.InMemoryMftRepository;
import com.edispark.mft.domain.repository.SecretStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ProductionCertificatePolicyTest {
    @Test
    void productionRejectsPrivateKeyUploadsBeforeReadingOrStoringMaterial() {
        RecordingSecretStore secrets = new RecordingSecretStore();
        CertificateService service = new CertificateService(new InMemoryMftRepository(), secrets,
                Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC), false);

        assertThatThrownBy(() -> service.upload("identity", CertificateKind.LOCAL_IDENTITY,
                new byte[]{1, 2, 3}, "password".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Private-key upload is disabled; register an AWS KMS identity instead");
        org.assertj.core.api.Assertions.assertThat(secrets.putCalls).isZero();
    }

    private static final class RecordingSecretStore implements SecretStore {
        private int putCalls;
        @Override public String put(String tenantId, String name, byte[] plaintext) {
            putCalls++;
            return "unused";
        }
        @Override public byte[] get(String tenantId, String reference) {
            throw new AssertionError("get must not be called");
        }
    }
}
