package com.edispark.mft.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record TransferAttempt(
        int number,
        Instant startedAt,
        @Schema(nullable = true) Instant completedAt,
        @Schema(nullable = true) Integer httpStatus,
        String outcome,
        @Schema(nullable = true) String detail) {
}
