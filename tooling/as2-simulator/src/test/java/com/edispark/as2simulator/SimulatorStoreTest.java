package com.edispark.as2simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SimulatorStoreTest {
    @Test
    void retainsInspectablePayloadEvidenceWithAnIntegrityHash() {
        SimulatorStore store = new SimulatorStore();
        byte[] edi = "UNH+1+INVOIC:D:96A:UN'".getBytes(StandardCharsets.US_ASCII);

        SimulatorStore.SimulatedMessage evidence = store.add("INBOUND", "message-1", "EDISPARK", "SIMULATOR",
                "invoice.edi", "application/edifact", edi, Instant.parse("2026-11-04T12:00:00Z"));

        assertThat(evidence.sha256()).hasSize(64);
        assertThat(store.content(evidence.evidenceId())).hasValueSatisfying(content -> assertThat(content).isEqualTo(edi));
        edi[0] = 'X';
        assertThat(store.content(evidence.evidenceId())).hasValueSatisfying(content ->
                assertThat(new String(content, StandardCharsets.US_ASCII)).startsWith("UNH+"));
    }

    @Test
    void rejectsPayloadsThatWouldMakeTheLocalInspectionStoreUnbounded() {
        SimulatorStore store = new SimulatorStore();

        assertThatThrownBy(() -> store.add("INBOUND", "message-1", "EDISPARK", "SIMULATOR", "large.edi",
                "application/edifact", new byte[SimulatorStore.MAX_PAYLOAD_BYTES + 1], Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 MiB");
    }
}
