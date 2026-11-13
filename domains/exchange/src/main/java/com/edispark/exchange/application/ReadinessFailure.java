package com.edispark.exchange.application;

import java.util.List;

/** Structured validation failure across an application port, without source values. */
public final class ReadinessFailure extends IllegalArgumentException {
    private final List<ReadinessIssue> issues;

    public ReadinessFailure(List<ReadinessIssue> issues) {
        super("The document does not satisfy the selected route's validation rules.");
        this.issues = List.copyOf(issues);
    }

    public List<ReadinessIssue> issues() { return issues; }
}
