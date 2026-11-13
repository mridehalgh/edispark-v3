package com.edispark.integrationapplication.persistence;

import com.edispark.exchange.application.OutboundControlAllocator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cottn.core.tenancy.TenantContext;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!openapi-export")
final class DynamoOutboundControlAllocator implements OutboundControlAllocator {
    private final IntegrationDynamoStore store;
    private final ObjectMapper json;
    DynamoOutboundControlAllocator(IntegrationDynamoStore store, ObjectMapper json) { this.store = store; this.json = json; }

    @Override public Control reserve(UUID connectionId, UUID exchangeId) {
        String tenant = TenantContext.getCurrentTenant().asString();
        String reservation = "OUTBOUND_CONTROL_RESERVATION#" + exchangeId;
        String counterKey = "OUTBOUND_CONTROL#" + connectionId;
        for (int attempt = 0; attempt < 16; attempt++) {
            var prior = store.get(tenant, reservation).map(value -> read(value, Reservation.class));
            if (prior.isPresent()) {
                if (!prior.get().connectionId().equals(connectionId)) throw new IllegalStateException("The exchange control belongs to another trading connection.");
                return prior.get().control();
            }
            var counter = store.get(tenant, counterKey).map(value -> read(value, Counter.class));
            if (counter.isEmpty()) {
                store.putVersionedIfCurrent(tenant, counterKey, "OUTBOUND_CONTROL", write(new Counter(0, 0, 0)), 0);
                continue;
            }
            var current = counter.get();
            if (current.fileGeneration() >= 9999 || current.transmission() >= 99999999999999L) {
                throw new IllegalStateException("The agreed transmission counter range is exhausted. Review its rollover with the trading partner before sending.");
            }
            var next = new Counter(current.transmission() + 1, current.fileGeneration() + 1, current.version() + 1);
            var control = new Control(next.transmission(), next.fileGeneration());
            if (store.putVersionedPairIfCurrent(tenant, counterKey, "OUTBOUND_CONTROL", write(next), current.version(), next.version(),
                    reservation, "OUTBOUND_CONTROL_RESERVATION", write(new Reservation(connectionId, control)))) return control;
        }
        throw new IllegalStateException("The transmission counter changed concurrently. Try the exchange again.");
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Could not retain transmission controls", exception); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (java.io.IOException exception) { throw new IllegalStateException("Could not read transmission controls", exception); }
    }
    private record Counter(long transmission, int fileGeneration, long version) { }
    private record Reservation(UUID connectionId, Control control) { }
}
