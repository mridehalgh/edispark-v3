package com.edispark.integrationapplication.persistence;

import com.edispark.partnerships.domain.model.SsccIssuer;
import com.edispark.partnerships.domain.model.SsccReservation;
import com.edispark.partnerships.domain.repository.SsccRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!openapi-export")
final class DynamoSsccRepository implements SsccRepository {
    private final IntegrationDynamoStore store;
    private final ObjectMapper json;

    DynamoSsccRepository(IntegrationDynamoStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    @Override public SsccIssuer save(SsccIssuer issuer) {
        if (!store.putVersionedIfCurrent(issuer.tenantId(), issuerKey(issuer.id()), "SSCC_ISSUER",
                write(issuer), issuer.version())) {
            throw new java.util.ConcurrentModificationException("SSCC range changed; reload and try again");
        }
        return issuer;
    }

    @Override public Optional<SsccIssuer> find(String tenantId, UUID id) {
        return store.get(tenantId, issuerKey(id)).map(value -> read(value, SsccIssuer.class));
    }

    @Override public List<SsccIssuer> list(String tenantId) {
        return store.list(tenantId, "SSCC_ISSUER#").stream()
                .map(value -> read(value, SsccIssuer.class)).toList();
    }

    @Override public Optional<SsccReservation> findReservation(String tenantId, String idempotencyKey) {
        return store.get(tenantId, reservationKey(idempotencyKey))
                .map(value -> read(value, SsccReservation.class));
    }

    @Override public boolean saveReservation(SsccIssuer issuer, long expectedVersion,
            SsccReservation reservation) {
        return store.putVersionedPairIfCurrent(issuer.tenantId(), issuerKey(issuer.id()), "SSCC_ISSUER",
                write(issuer), expectedVersion, issuer.version(), reservationKey(reservation.idempotencyKey()),
                "SSCC_RESERVATION", write(reservation));
    }

    private String issuerKey(UUID id) { return "SSCC_ISSUER#" + id; }
    private String reservationKey(String idempotencyKey) { return "SSCC_RESERVATION#" + idempotencyKey; }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Could not serialize SSCC configuration", exception); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception exception) { throw new IllegalStateException("Could not read SSCC configuration", exception); }
    }
}
