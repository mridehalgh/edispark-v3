package com.edispark.mft.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartnerProfileRolloverTest {

    @Test
    void signingCertificateRolloverRetainsPreviousCertificateOnlyForTheGraceWindow() {
        UUID originalSigningCertificate = UUID.randomUUID();
        UUID replacementSigningCertificate = UUID.randomUUID();
        UUID replacementEncryptionCertificate = UUID.randomUUID();
        Instant graceUntil = Instant.parse("2026-11-08T12:00:00Z");
        PartnerProfile profile = profile(originalSigningCertificate);

        PartnerProfile rotated = profile.withPartnerCertificates(
                replacementSigningCertificate, replacementEncryptionCertificate, graceUntil);

        assertThat(rotated.partnerSigningCertificateId()).isEqualTo(replacementSigningCertificate);
        assertThat(rotated.partnerEncryptionCertificateId()).isEqualTo(replacementEncryptionCertificate);
        assertThat(rotated.previousPartnerSigningCertificateId()).isEqualTo(originalSigningCertificate);
        assertThat(rotated.signingCertificateGraceUntil()).isEqualTo(graceUntil);
    }

    private PartnerProfile profile(UUID signingCertificateId) {
        return new PartnerProfile(UUID.randomUUID(), "tenant-a", "Retailer", "EDISPARK-A", "RETAILER-A",
                URI.create("https://retailer.example/as2"), UUID.randomUUID(), signingCertificateId,
                UUID.randomUUID(), null, null, null, MdnMode.SYNCHRONOUS, true, true, false, false,
                PartnerStatus.ACTIVE, Instant.parse("2026-11-01T12:00:00Z"));
    }
}
