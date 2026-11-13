package com.edispark.exchange.application;

import java.util.UUID;

/** Durable, idempotent control allocation after readiness succeeds, scoped to one trading connection. */
public interface OutboundControlAllocator {
    Control reserve(UUID connectionId, UUID exchangeId);
    record Control(long transmission, int fileGeneration) { }
    static OutboundControlAllocator unavailable() {
        return (connection, exchange) -> { throw new IllegalStateException("Durable transmission controls are unavailable."); };
    }
}
