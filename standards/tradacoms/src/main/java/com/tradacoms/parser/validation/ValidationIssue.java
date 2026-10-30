package com.tradacoms.parser.validation;

import java.util.Objects;

/**
 * Represents a single validation issue found during EDI validation.
 * Contains the error code, severity, message, location, and raw content snippet.
 */
public final class ValidationIssue {

    private final String code;
    private final Severity severity;
    private final String message;
    private final IssueLocation location;
    private final String rawSnippet;

    public ValidationIssue(
            String code,
            Severity severity,
            String message,
            IssueLocation location,
            String rawSnippet
    ) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.location = location;
        this.rawSnippet = rawSnippet;
    }

    /**
     * Creates an error-level validation issue.
     */
    public static ValidationIssue error(String code, String message, IssueLocation location, String rawSnippet) {
        return new ValidationIssue(code, Severity.ERROR, message, location, rawSnippet);
    }

    /**
     * Creates a warning-level validation issue.
     */
    public static ValidationIssue warning(String code, String message, IssueLocation location, String rawSnippet) {
        return new ValidationIssue(code, Severity.WARNING, message, location, rawSnippet);
    }

    /**
     * Creates an info-level validation issue.
     */
    public static ValidationIssue info(String code, String message, IssueLocation location, String rawSnippet) {
        return new ValidationIssue(code, Severity.INFO, message, location, rawSnippet);
    }

    /**
     * Creates an error-level validation issue without a raw snippet.
     */
    public static ValidationIssue error(String code, String message, IssueLocation location) {
        return error(code, message, location, null);
    }

    /**
     * Creates a warning-level validation issue without a raw snippet.
     */
    public static ValidationIssue warning(String code, String message, IssueLocation location) {
        return warning(code, message, location, null);
    }

    public String getCode() {
        return code;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public IssueLocation getLocation() {
        return location;
    }

    public String getRawSnippet() {
        return rawSnippet;
    }

    /**
     * Returns the category of this issue based on its error code.
     */
    public String getCategory() {
        return ErrorCode.getCategory(code);
    }

    /**
     * Returns true if this is an error-level issue.
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /**
     * Returns true if this is a warning-level issue.
     */
    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    /**
     * Returns true if this is an info-level issue.
     */
    public boolean isInfo() {
        return severity == Severity.INFO;
    }

    /**
     * Returns true if this issue has location information.
     */
    public boolean hasLocation() {
        return location != null;
    }

    /**
     * Returns true if this issue has a raw snippet.
     */
    public boolean hasRawSnippet() {
        return rawSnippet != null && !rawSnippet.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationIssue that = (ValidationIssue) o;
        return Objects.equals(code, that.code) &&
                severity == that.severity &&
                Objects.equals(message, that.message) &&
                Objects.equals(location, that.location) &&
                Objects.equals(rawSnippet, that.rawSnippet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, severity, message, location, rawSnippet);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ");
        sb.append(code).append(": ").append(message);
        if (location != null) {
            sb.append(" at ").append(location.toDisplayString());
        }
        if (rawSnippet != null && !rawSnippet.isEmpty()) {
            sb.append(" - '").append(truncateSnippet(rawSnippet, 50)).append("'");
        }
        return sb.toString();
    }

    private String truncateSnippet(String snippet, int maxLength) {
        if (snippet.length() <= maxLength) {
            return snippet;
        }
        return snippet.substring(0, maxLength - 3) + "...";
    }

    /**
     * Builder for creating ValidationIssue instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String code;
        private Severity severity = Severity.ERROR;
        private String message;
        private IssueLocation location;
        private String rawSnippet;

        private Builder() {}

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder location(IssueLocation location) {
            this.location = location;
            return this;
        }

        public Builder rawSnippet(String rawSnippet) {
            this.rawSnippet = rawSnippet;
            return this;
        }

        public ValidationIssue build() {
            return new ValidationIssue(code, severity, message, location, rawSnippet);
        }
    }
}
