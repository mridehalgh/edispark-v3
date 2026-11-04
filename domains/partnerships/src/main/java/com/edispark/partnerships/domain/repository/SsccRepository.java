package com.edispark.partnerships.domain.repository;

import com.edispark.partnerships.domain.model.SsccIssuer;
import com.edispark.partnerships.domain.model.SsccReservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SsccRepository {
    SsccIssuer save(SsccIssuer issuer);
    Optional<SsccIssuer> find(String tenantId, UUID id);
    List<SsccIssuer> list(String tenantId);
    Optional<SsccReservation> findReservation(String tenantId, String idempotencyKey);
    boolean saveReservation(SsccIssuer issuer, long expectedVersion, SsccReservation reservation);
}
