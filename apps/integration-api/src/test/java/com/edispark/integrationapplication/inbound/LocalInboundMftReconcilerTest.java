package com.edispark.integrationapplication.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocalInboundMftReconcilerTest {
    private static final String TENANT_ID = "d9c01ea5-f555-4c6d-9c87-1465ea78ac01";

    @Test
    void quarantinesAPermanentBadExportWithoutBlockingLaterTransfers() {
        InboundMftPayloadEvent bad = event("bad.edi");
        InboundMftPayloadEvent good = event("good.edi");
        Map<UUID, AtomicInteger> attempts = attempts(bad, good);
        var reconciler = new LocalInboundMftReconciler(new ModuleProbe(List.of(bad, good)), event -> {
            attempts.get(event.eventId()).incrementAndGet();
            if (event == bad) throw new IllegalArgumentException("invalid mapping input");
        }, TENANT_ID);

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(attempts.get(bad.eventId())).hasValue(1);
        assertThat(attempts.get(good.eventId())).hasValue(1);
    }

    @Test
    void retriesATransientFailureWithoutReprocessingSuccessfulTransfers() {
        InboundMftPayloadEvent transientFailure = event("temporarily-unavailable.edi");
        InboundMftPayloadEvent good = event("good.edi");
        Map<UUID, AtomicInteger> attempts = attempts(transientFailure, good);
        var reconciler = new LocalInboundMftReconciler(new ModuleProbe(List.of(transientFailure, good)), event -> {
            int attempt = attempts.get(event.eventId()).incrementAndGet();
            if (event == transientFailure && attempt == 1) {
                throw new IllegalStateException("temporary storage failure");
            }
        }, TENANT_ID);

        reconciler.reconcile();
        reconciler.reconcile();

        assertThat(attempts.get(transientFailure.eventId())).hasValue(2);
        assertThat(attempts.get(good.eventId())).hasValue(1);
    }

    private Map<UUID, AtomicInteger> attempts(InboundMftPayloadEvent... events) {
        Map<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();
        for (InboundMftPayloadEvent event : events) attempts.put(event.eventId(), new AtomicInteger());
        return attempts;
    }

    private InboundMftPayloadEvent event(String filename) {
        return new InboundMftPayloadEvent(UUID.randomUUID(), TENANT_ID, UUID.randomUUID(), UUID.randomUUID(),
                "<" + UUID.randomUUID() + "@as2.edispark.local>", filename, "application/edifact",
                "0".repeat(64), 1, Instant.parse("2026-11-04T12:00:00Z"));
    }

    private record ModuleProbe(List<InboundMftPayloadEvent> events) implements InboundModuleAccess {
        @Override public byte[] payload(InboundMftPayloadEvent event) { throw new UnsupportedOperationException(); }
        @Override public List<InboundMftPayloadEvent> inboundTransfers(String tenantId) { return events; }
        @Override public StoredDocument store(InboundDocument document) { throw new UnsupportedOperationException(); }
    }
}
