package com.edispark.partnerships.infrastructure;

import com.edispark.partnerships.domain.model.SsccIssuer;
import com.edispark.partnerships.domain.model.SsccReservation;
import com.edispark.partnerships.domain.repository.SsccRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "openapi-export"})
public final class InMemorySsccRepository implements SsccRepository {
    private final Map<UUID, SsccIssuer> issuers = new ConcurrentHashMap<>();
    private final Map<String, SsccReservation> reservations = new ConcurrentHashMap<>();

    @Override public synchronized SsccIssuer save(SsccIssuer issuer) {
        SsccIssuer current = issuers.get(issuer.id());
        boolean currentVersionMatches = current == null ? issuer.version() == 0
                : issuer.version() == current.version() + 1;
        if (!currentVersionMatches) {
            throw new java.util.ConcurrentModificationException("SSCC range changed; reload and try again");
        }
        issuers.put(issuer.id(), issuer);
        return issuer;
    }

    @Override public Optional<SsccIssuer> find(String tenantId, UUID id) {
        return Optional.ofNullable(issuers.get(id)).filter(item -> item.tenantId().equals(tenantId));
    }

    @Override public List<SsccIssuer> list(String tenantId) {
        return issuers.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList();
    }

    @Override public Optional<SsccReservation> findReservation(String tenantId, String idempotencyKey) {
        return Optional.ofNullable(reservations.get(key(tenantId, idempotencyKey)));
    }

    @Override public synchronized boolean saveReservation(SsccIssuer issuer, long expectedVersion,
            SsccReservation reservation) {
        SsccIssuer current = issuers.get(issuer.id());
        String reservationKey = key(issuer.tenantId(), reservation.idempotencyKey());
        if (current == null || current.version() != expectedVersion || reservations.containsKey(reservationKey)) {
            return false;
        }
        issuers.put(issuer.id(), issuer);
        reservations.put(reservationKey, reservation);
        return true;
    }

    private String key(String tenantId, String idempotencyKey) {
        return tenantId + "#" + idempotencyKey;
    }
}
