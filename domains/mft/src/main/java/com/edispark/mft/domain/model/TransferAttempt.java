package com.edispark.mft.domain.model;

import java.time.Instant;

public record TransferAttempt(
        int number,
        Instant startedAt,
        Instant completedAt,
        Integer httpStatus,
        String outcome,
        String detail) {
}
