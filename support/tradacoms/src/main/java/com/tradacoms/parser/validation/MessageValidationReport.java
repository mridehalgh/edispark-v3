package com.tradacoms.parser.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validation report for a single message within a batch.
 * Contains the validation status and all issues found for this message.
 */
public final class MessageValidationReport {

    private final int messageIndex;
    private final String messageControlRef;
    private final ValidationStatus status;
    private final List<ValidationIssue> issues;
    private final Map<String, Integer> issueCountsByCode;
    private final Map<Severity, Integer> issueCountsBySeverity;

    private MessageValidationReport(
            int messageIndex,
            String messageControlRef,
            ValidationStatus status,
            List<ValidationIssue> issues,
            Map<String, Integer> issueCountsByCode,
            Map<Severity, Integer> issueCountsBySeverity
    ) {
        this.messageIndex = messageIndex;
        this.messageControlRef = messageControlRef;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.issues = List.copyOf(issues);
        this.issueCountsByCode = Map.copyOf(issueCountsByCode);
        this.issueCountsBySeverity = Map.copyOf(issueCountsBySeverity);
    }

    /**
     * Creates a passing report with no issues.
     */
    public static MessageValidationReport pass(int messageIndex, String messageControlRef) {
        return new MessageValidationReport(
                messageIndex,
                messageControlRef,
                ValidationStatus.PASS,
                List.of(),
                Map.of(),
                Map.of()
        );
    }

    /**
     * Creates a report from a list of issues.
     */
    public static MessageValidationReport fromIssues(
            int messageIndex,
            String messageControlRef,
            List<ValidationIssue> issues
    ) {
        if (issues == null || issues.isEmpty()) {
            return pass(messageIndex, messageControlRef);
        }

        Map<String, Integer> countsByCode = new HashMap<>();
        Map<Severity, Integer> countsBySeverity = new EnumMap<>(Severity.class);
        boolean hasErrors = false;
        boolean hasWarnings = false;

        for (ValidationIssue issue : issues) {
            countsByCode.merge(issue.getCode(), 1, Integer::sum);
            countsBySeverity.merge(issue.getSeverity(), 1, Integer::sum);
            
            if (issue.getSeverity() == Severity.ERROR) {
                hasErrors = true;
            } else if (issue.getSeverity() == Severity.WARNING) {
                hasWarnings = true;
            }
        }

        ValidationStatus status = ValidationStatus.derive(hasErrors, hasWarnings);

        return new MessageValidationReport(
                messageIndex,
                messageControlRef,
                status,
                issues,
                countsByCode,
                countsBySeverity
        );
    }

    public int getMessageIndex() {
        return messageIndex;
    }

    public String getMessageControlRef() {
        return messageControlRef;
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }

    public Map<String, Integer> getIssueCountsByCode() {
        return issueCountsByCode;
    }

    public Map<Severity, Integer> getIssueCountsBySeverity() {
        return issueCountsBySeverity;
    }

    /**
     * Returns the total number of issues.
     */
    public int getTotalIssueCount() {
        return issues.size();
    }

    /**
     * Returns the number of errors.
     */
    public int getErrorCount() {
        return issueCountsBySeverity.getOrDefault(Severity.ERROR, 0);
    }

    /**
     * Returns the number of warnings.
     */
    public int getWarningCount() {
        return issueCountsBySeverity.getOrDefault(Severity.WARNING, 0);
    }

    /**
     * Returns true if this message passed validation.
     */
    public boolean isPassed() {
        return status == ValidationStatus.PASS;
    }

    /**
     * Returns true if this message has warnings but no errors.
     */
    public boolean hasWarningsOnly() {
        return status == ValidationStatus.WARN;
    }

    /**
     * Returns true if this message failed validation.
     */
    public boolean isFailed() {
        return status == ValidationStatus.FAIL;
    }

    /**
     * Returns all issues with the specified severity.
     */
    public List<ValidationIssue> getIssuesBySeverity(Severity severity) {
        return issues.stream()
                .filter(i -> i.getSeverity() == severity)
                .toList();
    }

    /**
     * Returns all issues with the specified code.
     */
    public List<ValidationIssue> getIssuesByCode(String code) {
        return issues.stream()
                .filter(i -> code.equals(i.getCode()))
                .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageValidationReport that = (MessageValidationReport) o;
        return messageIndex == that.messageIndex &&
                Objects.equals(messageControlRef, that.messageControlRef) &&
                status == that.status &&
                Objects.equals(issues, that.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageIndex, messageControlRef, status, issues);
    }

    @Override
    public String toString() {
        return "MessageValidationReport{" +
                "messageIndex=" + messageIndex +
                ", messageControlRef='" + messageControlRef + '\'' +
                ", status=" + status +
                ", issueCount=" + issues.size() +
                '}';
    }
}
