package com.edispark.partnerships.domain.model;

import java.time.Instant;
import java.util.List;

public record QualificationReport(boolean passed, List<String> checks, List<String> failures, Instant completedAt) {
    public QualificationReport {
        checks = List.copyOf(checks == null ? List.of() : checks);
        failures = List.copyOf(failures == null ? List.of() : failures);
        if (passed != failures.isEmpty()) throw new IllegalArgumentException("passed must agree with failures");
    }
}
