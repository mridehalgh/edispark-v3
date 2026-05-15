package com.example.documents.application.tradacoms;

import java.util.List;
import java.util.UUID;

public record TradacomsIngestionResult(
        UUID documentSetId,
        UUID documentId,
        int sourceVersionNumber,
        ParseStatus parseStatus,
        String messageType,
        List<String> parseErrors) {

    public TradacomsIngestionResult {
        parseErrors = parseErrors == null ? List.of() : List.copyOf(parseErrors);
    }
}
