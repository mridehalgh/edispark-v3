package com.edispark.partnerships.application;

import com.edispark.partnerships.domain.model.SsccIssuer;
import com.edispark.partnerships.domain.model.SsccIssuerStatus;
import com.edispark.partnerships.domain.model.SsccReservation;
import com.edispark.partnerships.domain.repository.SsccRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class SsccService {
    private static final int MAX_CONCURRENT_ATTEMPTS = 64;
    private static final Duration RESERVATION_TTL = Duration.ofHours(24);

    private final SsccRepository repository;
    private final Clock clock;

    public SsccService(SsccRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<SsccIssuer> listIssuers() {
        return repository.list();
    }

    public SsccIssuer createIssuer(String name, String prefix, int extensionDigit, long serialStart,
            long serialEnd, int reuseAfterMonths, boolean defaultIssuer, boolean ownershipAttested) {
        SsccIssuer issuer = repository.draft( name, prefix, extensionDigit, serialStart,
                serialEnd, reuseAfterMonths, defaultIssuer, ownershipAttested, clock.instant());
        if (repository.list().stream().anyMatch(issuer::overlaps)) {
            throw new IllegalArgumentException("SSCC serial range overlaps another configured range");
        }
        return repository.save(issuer);
    }

    public SsccIssuer activate(UUID id) {
        SsccIssuer active = repository.save(requireIssuer(id).activate(clock.instant()));
        if (active.defaultIssuer()) clearDefault(active.id());
        return active;
    }

    public SsccIssuer retire(UUID id) {
        return repository.save(requireIssuer(id).retire(clock.instant()));
    }

    public SsccIssuer makeDefault(UUID id) {
        SsccIssuer issuer = requireIssuer(id);
        if (issuer.status() != SsccIssuerStatus.ACTIVE) throw new IllegalStateException("Only an active issuer can be the default");
        clearDefault(id);
        return repository.save(issuer.withDefault(true, clock.instant()));
    }

    public SsccReservation reserve(UUID requestedIssuerId, int count, String idempotencyKey, String context) {
        var existing = repository.findReservation(idempotencyKey);
        if (existing.isPresent()) return verifyRetry(existing.get(), requestedIssuerId, count, context);

        for (int attempt = 0; attempt < MAX_CONCURRENT_ATTEMPTS; attempt++) {
            SsccIssuer issuer = requestedIssuerId == null ? defaultIssuer() : requireIssuer(requestedIssuerId);
            long expectedVersion = issuer.version();
            SsccIssuer.Allocation allocation = issuer.reserve(count, clock.instant());
            SsccReservation reservation = repository.reservation(issuer,
                    idempotencyKey, context, allocation.values(), clock.instant(), clock.instant().plus(RESERVATION_TTL));
            if (repository.saveReservation(allocation.issuer(), expectedVersion, reservation)) return reservation;
            existing = repository.findReservation(idempotencyKey);
            if (existing.isPresent()) return verifyRetry(existing.get(), requestedIssuerId, count, context);
            long backoffNanos = java.util.concurrent.ThreadLocalRandom.current()
                    .nextLong(100_000, Math.min(5_000_000, 100_000L * (attempt + 2)));
            java.util.concurrent.locks.LockSupport.parkNanos(backoffNanos);
        }
        throw new IllegalStateException("SSCC allocation is busy; retry with the same idempotency key");
    }

    private SsccReservation verifyRetry(SsccReservation reservation, UUID issuerId, int count, String context) {
        if ((issuerId != null && !issuerId.equals(reservation.issuerId()))
                || count != reservation.ssccs().size() || !context.equals(reservation.context())) {
            throw new IllegalArgumentException("Idempotency key was already used for a different SSCC request");
        }
        return reservation;
    }

    private SsccIssuer defaultIssuer() {
        return repository.list().stream()
                .filter(item -> item.defaultIssuer() && item.status() == SsccIssuerStatus.ACTIVE)
                .findFirst().orElseThrow(() -> new IllegalStateException("Configure an active default SSCC issuer first"));
    }

    private SsccIssuer requireIssuer(UUID id) {
        return repository.find(id)
                .orElseThrow(() -> new IllegalArgumentException("SSCC issuer was not found"));
    }

    private void clearDefault(UUID except) {
        repository.list().stream().filter(SsccIssuer::defaultIssuer)
                .filter(item -> except == null || !except.equals(item.id()))
                .map(item -> item.withDefault(false, clock.instant())).forEach(repository::save);
    }
}
