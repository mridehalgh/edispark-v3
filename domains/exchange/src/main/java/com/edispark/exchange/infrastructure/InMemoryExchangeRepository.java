package com.edispark.exchange.infrastructure;

import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "openapi-export"})
public final class InMemoryExchangeRepository implements ExchangeRepository {
    private final Map<UUID, EdiExchange> exchanges = new ConcurrentHashMap<>();
    private final Map<String, UUID> idempotency = new ConcurrentHashMap<>();
    @Override public synchronized EdiExchange save(EdiExchange exchange) {
        UUID existing = idempotency.putIfAbsent(exchange.tenantId() + ":" + exchange.idempotencyKey(), exchange.id());
        if (existing != null && !existing.equals(exchange.id())) return exchanges.get(existing);
        exchanges.put(exchange.id(), exchange); return exchange;
    }
    @Override public Optional<EdiExchange> find(String tenantId, UUID id) { return Optional.ofNullable(exchanges.get(id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public Optional<EdiExchange> findByIdempotencyKey(String tenantId, String key) { return Optional.ofNullable(idempotency.get(tenantId + ":" + key)).flatMap(id -> find(tenantId, id)); }
    @Override public List<EdiExchange> list(String tenantId) { return exchanges.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList(); }
}
