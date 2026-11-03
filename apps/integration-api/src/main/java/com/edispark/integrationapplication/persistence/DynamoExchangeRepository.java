package com.edispark.integrationapplication.persistence;

import com.edispark.exchange.domain.DocumentReference;
import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeRepository;
import com.edispark.exchange.domain.ExchangeStage;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local & !openapi-export")
final class DynamoExchangeRepository implements ExchangeRepository {
    private final IntegrationDynamoStore store;
    private final ObjectMapper json;
    DynamoExchangeRepository(IntegrationDynamoStore store, ObjectMapper json) { this.store = store; this.json = json; }

    @Override public EdiExchange save(EdiExchange exchange) {
        Optional<EdiExchange> existing = findByIdempotencyKey(exchange.tenantId(), exchange.idempotencyKey());
        if (existing.isPresent() && !existing.get().id().equals(exchange.id())) return existing.get();
        if (existing.isEmpty()) {
            boolean claimed = store.putPairIfAbsent(exchange.tenantId(), "EXCHANGE#" + exchange.id(), "EXCHANGE",
                    write(Snapshot.from(exchange)), "IDEMPOTENCY#" + exchange.idempotencyKey(), exchange.id().toString());
            if (!claimed) return findByIdempotencyKey(exchange.tenantId(), exchange.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Idempotency claim could not be resolved"));
            return exchange;
        }
        store.put(exchange.tenantId(), "EXCHANGE#" + exchange.id(), "EXCHANGE", write(Snapshot.from(exchange)));
        return exchange;
    }
    @Override public Optional<EdiExchange> find(String tenantId, UUID id) {
        return store.get(tenantId, "EXCHANGE#" + id).map(this::snapshot).map(Snapshot::toDomain);
    }
    @Override public Optional<EdiExchange> findByIdempotencyKey(String tenantId, String key) {
        return store.get(tenantId, "IDEMPOTENCY#" + key).map(UUID::fromString).flatMap(id -> find(tenantId, id));
    }
    @Override public List<EdiExchange> list(String tenantId) {
        return store.list(tenantId, "EXCHANGE#").stream().map(this::snapshot).map(Snapshot::toDomain).toList();
    }
    private String write(Snapshot value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Could not serialize exchange", e); } }
    private Snapshot snapshot(String value) { try { return json.readValue(value, Snapshot.class); } catch (Exception e) { throw new IllegalStateException("Could not read exchange", e); } }

    private record Snapshot(UUID id, String tenantId, ExchangeDirection direction, UUID partnerId, UUID agreementId,
            int agreementRevision, UUID capabilityId, String idempotencyKey, DocumentReference sourceDocument,
            DocumentReference generatedDocument, UUID mftTransferId, ExchangeStatus status, Instant createdAt,
            Instant updatedAt, List<ExchangeStage> stages, int messageOrdinal, String sourceMessageReference) {
        static Snapshot from(EdiExchange value) { return new Snapshot(value.id(), value.tenantId(), value.direction(),
                value.partnerId(), value.agreementId(), value.agreementRevision(), value.capabilityId(),
                value.idempotencyKey(), value.sourceDocument(), value.generatedDocument(), value.mftTransferId(),
                value.status(), value.createdAt(), value.updatedAt(), value.stages(), value.messageOrdinal(),
                value.sourceMessageReference()); }
        EdiExchange toDomain() { return EdiExchange.restore(id, tenantId, direction, partnerId, agreementId,
                agreementRevision, capabilityId, idempotencyKey, sourceDocument, generatedDocument, mftTransferId,
                status, createdAt, updatedAt, stages, messageOrdinal, sourceMessageReference); }
    }
}
