package com.edispark.mft.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class TransferStatePropertyTest {
    /** Feature: mft-as2, Property 4: delivered transfers remain terminal. */
    @Property(tries = 100)
    void deliveredTransfersCannotReturnToActiveDelivery(@ForAll String suffix) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Transfer transfer = Transfer.outbound("tenant", UUID.randomUUID(), "file-" + suffix.hashCode() + ".edi",
                "application/edi-x12", "<message@example.test>", UUID.randomUUID().toString(), null, now);
        transfer.attachPayload(new PayloadRef("tenant/payload", "ab", 2), now);
        transfer.startAttempt(now);
        transfer.deliver("processed", now.plusSeconds(1));

        assertThat(transfer.status()).isEqualTo(TransferStatus.DELIVERED);
        assertThatThrownBy(() -> transfer.scheduleRetry("late retry", now.plusSeconds(3), now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }
}
