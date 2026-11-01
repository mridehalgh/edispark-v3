package com.edispark.exchange.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRepository {
    EdiExchange save(EdiExchange exchange);
    Optional<EdiExchange> find(String tenantId, UUID id);
    Optional<EdiExchange> findByIdempotencyKey(String tenantId, String key);
    List<EdiExchange> list(String tenantId);
}
