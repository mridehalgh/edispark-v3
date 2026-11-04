package com.edispark.mft.api.dto;

import java.time.Instant;
import java.util.Map;

public record MftErrorResponse(String code, String message, Instant timestamp, String stage, boolean retryable,
        String recommendedAction, String correlationId, Map<String, Object> details) {
    public static MftErrorResponse of(String code, String message) {
        return actionable(code, message, "MFT", false, "Correct the request or configuration before retrying.", null);
    }

    public static MftErrorResponse actionable(String code, String message, String stage, boolean retryable,
            String recommendedAction, String correlationId) {
        return new MftErrorResponse(code, message, Instant.now(), stage, retryable, recommendedAction,
                correlationId, Map.of());
    }
}
