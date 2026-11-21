package com.edispark.exchange.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRepository extends ExchangeCreation {
    EdiExchange save(EdiExchange exchange);
    Optional<EdiExchange> find(UUID id);
    Optional<EdiExchange> findByIdempotencyKey(String key);
    List<EdiExchange> list();
    Page page(UUID after, int limit);

    record Page(List<EdiExchange> exchanges, UUID next) {}
}
