package com.tradacoms.parser.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Validation report for an entire batch.
 * Contains overall status, batch-level issues, and per-message validation reports.
 */
public final class BatchValidationReport {

    private final ValidationStatus overallStatus;
    private final List<ValidationIssue> batchLevelIssues;
    private final Map<Integer, MessageValidationReport> messageReports;
    private final Map<String, Integer> issueCountsByCode;
    private final Map<Severity, Integer> issueCountsBySeverity;

    private BatchValidationReport(
            ValidationStatus overallStatus,
            List<ValidationIssue> batchLevelIssues,
            Map<Integer, MessageValidationReport> messageReports,
            Map<String, Integer> issueCountsByCode,
            Map<Severity, Integer> issueCountsBySeverity
    ) {
        this.overallStatus = Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        this.batchLevelIssues = List.copyOf(batchLevelIssues);
        this.messageReports = Map.copyOf(messageReports);
        this.issueCountsByCode = Map.copyOf(issueCountsByCode);
        this.issueCountsBySeverity = Map.copyOf(issueCountsBySeverity);
    }

    /**
     * Creates a passing report with no issues.
     */
    public static BatchValidationReport pass() {
        return new BatchValidationReport(
                ValidationStatus.PASS,
                List.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    /**
     * Creates a report from batch-level issues and message reports.
     */
    public static BatchValidationReport fromResults(
            List<ValidationIssue> batchLevelIssues,
            Map<Integer, MessageValidationReport> messageReports
    ) {
        Map<String, Integer> countsByCode = new HashMap<>();
        Map<Severity, Integer> countsBySeverity = new EnumMap<>(Severity.class);
        boolean hasErrors = false;
        boolean hasWarnings = false;

        // Count batch-level issues
        for (ValidationIssue issue : batchLevelIssues) {
            countsByCode.merge(issue.getCode(), 1, Integer::sum);
            countsBySeverity.merge(issue.getSeverity(), 1, Integer::sum);
            
            if (issue.getSeverity() == Severity.ERROR) {
                hasErrors = true;
            } else if (issue.getSeverity() == Severity.WARNING) {
                hasWarnings = true;
            }
        }

        // Count message-level issues
        for (MessageValidationReport msgReport : messageReports.values()) {
            for (ValidationIssue issue : msgReport.getIssues()) {
                countsByCode.merge(issue.getCode(), 1, Integer::sum);
                countsBySeverity.merge(issue.getSeverity(), 1, Integer::sum);
            }
            
            if (msgReport.getStatus() == ValidationStatus.FAIL) {
                hasErrors = true;
            } else if (msgReport.getStatus() == ValidationStatus.WARN) {
                hasWarnings = true;
            }
        }

        ValidationStatus overallStatus = ValidationStatus.derive(hasErrors, hasWarnings);

        return new BatchValidationReport(
                overallStatus,
                batchLevelIssues,
                messageReports,
                countsByCode,
                countsBySeverity
        );
    }

    public ValidationStatus getOverallStatus() {
        return overallStatus;
    }

    public List<ValidationIssue> getBatchLevelIssues() {
        return batchLevelIssues;
    }

    public Map<Integer, MessageValidationReport> getMessageReports() {
        return messageReports;
    }

    public Map<String, Integer> getIssueCountsByCode() {
        return issueCountsByCode;
    }

    public Map<Severity, Integer> getIssueCountsBySeverity() {
        return issueCountsBySeverity;
    }

    /**
     * Returns the message report for the specified message index.
     */
    public Optional<MessageValidationReport> getMessageReport(int messageIndex) {
        return Optional.ofNullable(messageReports.get(messageIndex));
    }

    /**
     * Returns the total number of issues across all messages and batch level.
     */
    public int getTotalIssueCount() {
        int total = batchLevelIssues.size();
        for (MessageValidationReport msgReport : messageReports.values()) {
            total += msgReport.getTotalIssueCount();
        }
        return total;
    }

    /**
     * Returns the total number of errors.
     */
    public int getErrorCount() {
        return issueCountsBySeverity.getOrDefault(Severity.ERROR, 0);
    }

    /**
     * Returns the total number of warnings.
     */
    public int getWarningCount() {
        return issueCountsBySeverity.getOrDefault(Severity.WARNING, 0);
    }

    /**
     * Returns the number of messages validated.
     */
    public int getMessageCount() {
        return messageReports.size();
    }

    /**
     * Returns the number of messages that passed validation.
     */
    public int getPassedMessageCount() {
        return (int) messageReports.values().stream()
                .filter(MessageValidationReport::isPassed)
                .count();
    }

    /**
     * Returns the number of messages that failed validation.
     */
    public int getFailedMessageCount() {
        return (int) messageReports.values().stream()
                .filter(MessageValidationReport::isFailed)
                .count();
    }

    /**
     * Returns true if the batch passed validation.
     */
    public boolean isPassed() {
        return overallStatus == ValidationStatus.PASS;
    }

    /**
     * Returns true if the batch has warnings but no errors.
     */
    public boolean hasWarningsOnly() {
        return overallStatus == ValidationStatus.WARN;
    }

    /**
     * Returns true if the batch failed validation.
     */
    public boolean isFailed() {
        return overallStatus == ValidationStatus.FAIL;
    }

    /**
     * Returns all issues across batch and messages.
     */
    public List<ValidationIssue> getAllIssues() {
        List<ValidationIssue> all = new ArrayList<>(batchLevelIssues);
        for (MessageValidationReport msgReport : messageReports.values()) {
            all.addAll(msgReport.getIssues());
        }
        return List.copyOf(all);
    }

    /**
     * Returns all issues with the specified severity.
     */
    public List<ValidationIssue> getIssuesBySeverity(Severity severity) {
        return getAllIssues().stream()
                .filter(i -> i.getSeverity() == severity)
                .toList();
    }

    /**
     * Returns all issues with the specified code.
     */
    public List<ValidationIssue> getIssuesByCode(String code) {
        return getAllIssues().stream()
                .filter(i -> code.equals(i.getCode()))
                .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchValidationReport that = (BatchValidationReport) o;
        return overallStatus == that.overallStatus &&
                Objects.equals(batchLevelIssues, that.batchLevelIssues) &&
                Objects.equals(messageReports, that.messageReports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(overallStatus, batchLevelIssues, messageReports);
    }

    @Override
    public String toString() {
        return "BatchValidationReport{" +
                "overallStatus=" + overallStatus +
                ", batchLevelIssueCount=" + batchLevelIssues.size() +
                ", messageCount=" + messageReports.size() +
                ", totalIssueCount=" + getTotalIssueCount() +
                '}';
    }

    /**
     * Builder for creating BatchValidationReport instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<ValidationIssue> batchLevelIssues = new ArrayList<>();
        private final Map<Integer, MessageValidationReport> messageReports = new LinkedHashMap<>();

        private Builder() {}

        public Builder addBatchIssue(ValidationIssue issue) {
            batchLevelIssues.add(issue);
            return this;
        }

        public Builder addBatchIssues(List<ValidationIssue> issues) {
            batchLevelIssues.addAll(issues);
            return this;
        }

        public Builder addMessageReport(int messageIndex, MessageValidationReport report) {
            messageReports.put(messageIndex, report);
            return this;
        }

        public Builder addMessageReport(MessageValidationReport report) {
            messageReports.put(report.getMessageIndex(), report);
            return this;
        }

        public BatchValidationReport build() {
            return BatchValidationReport.fromResults(batchLevelIssues, messageReports);
        }
    }
}
