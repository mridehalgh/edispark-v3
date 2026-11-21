package com.edispark.partnerships.domain.repository;

import com.edispark.partnerships.domain.model.SsccIssuer;
import com.edispark.partnerships.domain.model.SsccReservation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SsccRepository extends SsccCreation {
    SsccIssuer save(SsccIssuer issuer);
    Optional<SsccIssuer> find(UUID id);
    List<SsccIssuer> list();
    Optional<SsccReservation> findReservation(String idempotencyKey);
    boolean saveReservation(SsccIssuer issuer, long expectedVersion, SsccReservation reservation);
}
