package com.tradacoms.parser;

import com.tradacoms.parser.validation.ValidationIssue;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Exception thrown when EDI validation fails.
 * Contains a list of validation issues that describe the problems found.
 * 
 * <p>Unlike other exceptions, validation typically returns issues without throwing.
 * This exception is used when validation is configured to fail-fast or when
 * programmatic validation requires exception-based error handling.
 * 
 * <p>Extends {@link EdiException} to provide a consistent exception hierarchy.
 * 
 * <p>Requirements: 10.5, 10.6
 */
public class ValidationException extends EdiException {

    private final List<ValidationIssue> issues;

    /**
     * Creates a ValidationException with a message and list of issues.
     */
    public ValidationException(String message, List<ValidationIssue> issues) {
        this(message, null, issues, null);
    }

    /**
     * Creates a ValidationException with a message, error code, and list of issues.
     */
    public ValidationException(String message, String errorCode, List<ValidationIssue> issues) {
        this(message, errorCode, issues, null);
    }

    /**
     * Creates a ValidationException with all parameters.
     */
    public ValidationException(String message, String errorCode, List<ValidationIssue> issues, Throwable cause) {
        super(message, errorCode, buildContext(issues), cause);
        this.issues = issues != null ? List.copyOf(issues) : List.of();
    }

    /**
     * Creates a ValidationException from a single issue.
     */
    public static ValidationException fromIssue(ValidationIssue issue) {
        Objects.requireNonNull(issue, "issue must not be null");
        return new ValidationException(issue.getMessage(), issue.getCode(), List.of(issue));
    }

    /**
     * Creates a ValidationException from multiple issues.
     */
    public static ValidationException fromIssues(List<ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return new ValidationException("Validation failed", List.of());
        }
        String message = issues.size() == 1 
                ? issues.get(0).getMessage()
                : String.format("Validation failed with %d issues", issues.size());
        String errorCode = issues.get(0).getCode();
        return new ValidationException(message, errorCode, issues);
    }

    /**
     * Builds an ErrorContext from the first issue in the list.
     */
    private static ErrorContext buildContext(List<ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return ErrorContext.empty();
        }
        
        ValidationIssue first = issues.get(0);
        ErrorContext.Builder builder = ErrorContext.builder();
        
        if (first.getLocation() != null) {
            var loc = first.getLocation();
            builder.filename(loc.filename())
                    .messageIndex(loc.messageIndex())
                    .segmentTag(loc.segmentTag())
                    .elementPosition(loc.elementPosition())
                    .componentPosition(loc.componentPosition())
                    .lineNumber(loc.lineNumber());
        }
        
        if (first.getRawSnippet() != null) {
            builder.rawSnippet(first.getRawSnippet());
        }
        
        return builder.build();
    }

    /**
     * Returns the list of validation issues.
     * 
     * @return an unmodifiable list of validation issues
     */
    public List<ValidationIssue> getIssues() {
        return issues;
    }

    /**
     * Returns the number of validation issues.
     */
    public int getIssueCount() {
        return issues.size();
    }

    /**
     * Returns true if there are any validation issues.
     */
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    /**
     * Returns the number of error-level issues.
     */
    public long getErrorCount() {
        return issues.stream().filter(ValidationIssue::isError).count();
    }

    /**
     * Returns the number of warning-level issues.
     */
    public long getWarningCount() {
        return issues.stream().filter(ValidationIssue::isWarning).count();
    }

    /**
     * Returns only the error-level issues.
     */
    public List<ValidationIssue> getErrors() {
        return issues.stream()
                .filter(ValidationIssue::isError)
                .collect(Collectors.toList());
    }

    /**
     * Returns only the warning-level issues.
     */
    public List<ValidationIssue> getWarnings() {
        return issues.stream()
                .filter(ValidationIssue::isWarning)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ValidationException{");
        sb.append("message='").append(getMessage()).append("'");
        if (getErrorCode() != null) {
            sb.append(", errorCode='").append(getErrorCode()).append("'");
        }
        sb.append(", issueCount=").append(issues.size());
        if (!issues.isEmpty()) {
            sb.append(", issues=[");
            for (int i = 0; i < Math.min(issues.size(), 3); i++) {
                if (i > 0) sb.append(", ");
                sb.append(issues.get(i).getCode()).append(": ").append(issues.get(i).getMessage());
            }
            if (issues.size() > 3) {
                sb.append(", ... ").append(issues.size() - 3).append(" more");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }
}
