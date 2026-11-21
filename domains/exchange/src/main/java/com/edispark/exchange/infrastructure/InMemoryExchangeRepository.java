package com.edispark.exchange.infrastructure;

import io.cottn.core.tenancy.TenantAccess;

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
@Profile("openapi-export")
public final class InMemoryExchangeRepository implements ExchangeRepository, ExchangeCreationAdapter {
    private final Map<String, EdiExchange> exchanges = new ConcurrentHashMap<>();
    private final Map<String, UUID> idempotency = new ConcurrentHashMap<>();
    @Override public synchronized EdiExchange save(EdiExchange exchange) { TenantAccess.requireOwner(exchange.tenantId());
        UUID existing = idempotency.putIfAbsent(exchange.tenantId() + ":" + exchange.idempotencyKey(), exchange.id());
        if (existing != null && !existing.equals(exchange.id())) return exchanges.get(exchange.tenantId() + ":" + existing);
        exchanges.put(exchange.tenantId() + ":" + exchange.id(), exchange); return exchange;
    }
    @Override public Optional<EdiExchange> find(UUID id) { String tenantId = TenantAccess.currentId(); return Optional.ofNullable(exchanges.get(tenantId + ":" + id)).filter(item -> item.tenantId().equals(tenantId)); }
    @Override public Optional<EdiExchange> findByIdempotencyKey(String key) { String tenantId = TenantAccess.currentId(); return Optional.ofNullable(idempotency.get(tenantId + ":" + key)).flatMap(id -> find(id)); }
    @Override
    public Page page(UUID after, int limit) {
        var matches = list().stream()
                .filter(exchange -> after == null || exchange.id().toString().compareTo(after.toString()) > 0)
                .sorted(java.util.Comparator.comparing(exchange -> exchange.id().toString()))
                .limit(limit + 1L).toList();
        return matches.size() > limit ? new Page(matches.subList(0, limit), matches.get(limit - 1).id())
                : new Page(matches, null);
    }

    @Override public List<EdiExchange> list() { String tenantId = TenantAccess.currentId(); return exchanges.values().stream().filter(item -> item.tenantId().equals(tenantId)).toList(); }
}
