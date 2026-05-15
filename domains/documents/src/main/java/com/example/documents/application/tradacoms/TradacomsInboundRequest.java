package com.example.documents.application.tradacoms;

import java.util.Map;
import java.util.Objects;

public record TradacomsInboundRequest(
        byte[] payload,
        String tenantId,
        String sourceFileName,
        String receivedBy,
        Map<String, String> interchangeMetadata) {

    public TradacomsInboundRequest {
        Objects.requireNonNull(payload, "Payload cannot be null");
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null");
        Objects.requireNonNull(sourceFileName, "Source file name cannot be null");
        Objects.requireNonNull(receivedBy, "Received by cannot be null");
        interchangeMetadata = interchangeMetadata == null ? Map.of() : Map.copyOf(interchangeMetadata);
    }
}
