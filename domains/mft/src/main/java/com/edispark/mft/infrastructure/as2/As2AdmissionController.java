package com.edispark.mft.infrastructure.as2;

import com.edispark.mft.domain.model.PartnerProfile;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** Instance-level load shedding. Edge and deployment-wide quotas remain infrastructure controls. */
public final class As2AdmissionController {
    private final Semaphore globalConcurrency;
    private final int partnerConcurrency;
    private final int requestsPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, PartnerLimit> partners = new ConcurrentHashMap<>();

    public As2AdmissionController(int globalConcurrency, int partnerConcurrency,
                                  int requestsPerMinute, Clock clock) {
        if (globalConcurrency < 1 || partnerConcurrency < 1 || requestsPerMinute < 1) {
            throw new IllegalArgumentException("AS2 admission limits must be positive");
        }
        this.globalConcurrency = new Semaphore(globalConcurrency);
        this.partnerConcurrency = partnerConcurrency;
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    public Permit acquire(PartnerProfile partner) {
        PartnerLimit limit = partners.computeIfAbsent(partner.id(), ignored ->
                new PartnerLimit(partnerConcurrency, requestsPerMinute, clock.millis()));
        if (!globalConcurrency.tryAcquire()) throw new As2AdmissionRejectedException();
        if (!limit.tryAcquire(clock.millis())) {
            globalConcurrency.release();
            throw new As2AdmissionRejectedException();
        }
        return new Permit(globalConcurrency, limit.concurrency);
    }

    public Permit acquireGlobal() {
        if (!globalConcurrency.tryAcquire()) throw new As2AdmissionRejectedException();
        return new Permit(globalConcurrency, null);
    }

    public Permit acquirePartner(PartnerProfile partner) {
        PartnerLimit limit = partners.computeIfAbsent(partner.id(), ignored ->
                new PartnerLimit(partnerConcurrency, requestsPerMinute, clock.millis()));
        if (!limit.tryAcquire(clock.millis())) throw new As2AdmissionRejectedException();
        return new Permit(null, limit.concurrency);
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore global;
        private final Semaphore partner;
        private boolean closed;

        private Permit(Semaphore global, Semaphore partner) {
            this.global = global;
            this.partner = partner;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            if (partner != null) partner.release();
            if (global != null) global.release();
        }
    }

    private static final class PartnerLimit {
        private static final long WINDOW_MILLIS = 60_000;
        private final Semaphore concurrency;
        private final int requestsPerMinute;
        private long windowStartedAt;
        private int requests;

        private PartnerLimit(int concurrency, int requestsPerMinute, long now) {
            this.concurrency = new Semaphore(concurrency);
            this.requestsPerMinute = requestsPerMinute;
            this.windowStartedAt = now;
        }

        private synchronized boolean tryAcquire(long now) {
            if (now - windowStartedAt >= WINDOW_MILLIS) {
                windowStartedAt = now;
                requests = 0;
            }
            if (requests >= requestsPerMinute || !concurrency.tryAcquire()) return false;
            requests++;
            return true;
        }
    }
}
