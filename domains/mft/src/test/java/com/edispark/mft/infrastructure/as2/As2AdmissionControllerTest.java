package com.edispark.mft.infrastructure.as2;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class As2AdmissionControllerTest {
    @Test
    void rejectsConcurrentAndExcessRequestsForOnePartnership() {
        As2AdmissionController controller = new As2AdmissionController(1, 1, 2,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC));
        PartnerProfile partner = partner();

        try (As2AdmissionController.Permit ignored = controller.acquire(partner)) {
            assertThatThrownBy(() -> controller.acquire(partner))
                    .isInstanceOf(As2AdmissionRejectedException.class);
        }
        try (As2AdmissionController.Permit ignored = controller.acquire(partner)) {
            // The second request in the rate window is accepted.
        }
        assertThatThrownBy(() -> controller.acquire(partner))
                .isInstanceOf(As2AdmissionRejectedException.class);
    }

    private PartnerProfile partner() {
        return new PartnerProfile(UUID.randomUUID(), "tenant-a", "Retailer", "EDISPARK", "RETAILER",
                URI.create("https://retailer.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, false, false, Instant.parse("2026-11-01T12:00:00Z"));
    }
}
