package com.example.documents.application.tradacoms;

import java.util.List;

public record TradacomsParseResult(
        ParseStatus status,
        String messageType,
        String businessDocumentNumber,
        List<String> errors) {

    public TradacomsParseResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
