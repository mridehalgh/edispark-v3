package com.edispark.mft.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartnerProfileLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");

    @Test
    void preservesFamilyIdentityAcrossEditableAndScheduledRevisions() {
        PartnerProfile active = active();
        PartnerProfile draft = active.newDraft(NOW);
        PartnerProfile revised = draft.revise("Retailer UK", URI.create("https://partner.example/as2-v2"), null,
                MdnMode.SYNCHRONOUS, true, true, false, false, NOW.plusSeconds(1));
        Instant cutover = NOW.plusSeconds(60);
        PartnerProfile scheduled = revised.schedule(cutover, NOW.plusSeconds(2));

        assertThat(scheduled.familyId()).isEqualTo(active.id());
        assertThat(scheduled.revision()).isEqualTo(2);
        assertThat(scheduled.activateDue(cutover).status()).isEqualTo(PartnerStatus.ACTIVE);
        assertThatThrownBy(() -> scheduled.archive(NOW)).hasMessageContaining("scheduled");
    }

    private PartnerProfile active() {
        return new PartnerProfile(UUID.randomUUID(), "tenant-a", "Retailer", "EDISPARK", "RETAILER",
                URI.create("https://partner.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, false, false, NOW.minusSeconds(60));
    }
}
