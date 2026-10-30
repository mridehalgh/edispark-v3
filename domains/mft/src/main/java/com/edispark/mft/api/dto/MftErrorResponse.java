package com.edispark.mft.api.dto;

import java.time.Instant;
import java.util.Map;

public record MftErrorResponse(String code, String message, Instant timestamp, Map<String, Object> details) {
    public static MftErrorResponse of(String code, String message) {
        return new MftErrorResponse(code, message, Instant.now(), Map.of());
    }
}
