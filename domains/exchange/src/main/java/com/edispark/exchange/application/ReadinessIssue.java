package com.edispark.exchange.application;

/** An actionable, payload-free issue. Owners distinguish user corrections from configuration work. */
public record ReadinessIssue(String code, String path, String message, String owner, String severity, String action) {
    public static ReadinessIssue document(String code, String path, String message) {
        return new ReadinessIssue(code, path, message, "DOCUMENT", "ERROR", "Create a corrected draft and fix the indicated field.");
    }
    public static ReadinessIssue configuration(String code, String message) {
        return new ReadinessIssue(code, "/", message, "CONFIGURATION", "ERROR", "Ask a workspace administrator to review the selected partner route.");
    }
}
