package com.tradacoms.parser.batch;

import java.util.Objects;

/**
 * Summary statistics for batch processing operations.
 */
public final class ProcessingSummary {

    private final int totalFiles;
    private final int successfulFiles;
    private final int partialFiles;
    private final int failedFiles;
    private final int totalMessages;
    private final int validMessages;
    private final int invalidMessages;
    private final int skippedMessages;
    private final int totalIssues;

    public ProcessingSummary(
            int totalFiles,
            int successfulFiles,
            int partialFiles,
            int failedFiles,
            int totalMessages,
            int validMessages,
            int invalidMessages,
            int skippedMessages,
            int totalIssues
    ) {
        this.totalFiles = totalFiles;
        this.successfulFiles = successfulFiles;
        this.partialFiles = partialFiles;
        this.failedFiles = failedFiles;
        this.totalMessages = totalMessages;
        this.validMessages = validMessages;
        this.invalidMessages = invalidMessages;
        this.skippedMessages = skippedMessages;
        this.totalIssues = totalIssues;
    }

    /**
     * Creates a summary from a list of file results.
     */
    public static ProcessingSummary fromResults(java.util.List<FileResult> results) {
        int totalFiles = results.size();
        int successfulFiles = 0;
        int partialFiles = 0;
        int failedFiles = 0;
        int totalMessages = 0;
        int validMessages = 0;
        int invalidMessages = 0;
        int skippedMessages = 0;
        int totalIssues = 0;

        for (FileResult fr : results) {
            switch (fr.getStatus()) {
                case SUCCESS -> successfulFiles++;
                case PARTIAL -> partialFiles++;
                case FAILED -> failedFiles++;
            }
            
            totalMessages += fr.getMessageCount();
            validMessages += fr.getValidMessageCount();
            invalidMessages += fr.getInvalidMessageCount();
            skippedMessages += fr.getSkippedMessageCount();
            totalIssues += fr.getTotalIssueCount();
        }

        return new ProcessingSummary(
                totalFiles, successfulFiles, partialFiles, failedFiles,
                totalMessages, validMessages, invalidMessages, skippedMessages,
                totalIssues
        );
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public int getSuccessfulFiles() {
        return successfulFiles;
    }

    public int getPartialFiles() {
        return partialFiles;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public int getTotalMessages() {
        return totalMessages;
    }

    public int getValidMessages() {
        return validMessages;
    }

    public int getInvalidMessages() {
        return invalidMessages;
    }

    public int getSkippedMessages() {
        return skippedMessages;
    }

    public int getTotalIssues() {
        return totalIssues;
    }

    /**
     * Returns true if all files were processed successfully.
     */
    public boolean isAllSuccessful() {
        return failedFiles == 0 && partialFiles == 0;
    }

    /**
     * Returns true if any files failed.
     */
    public boolean hasFailures() {
        return failedFiles > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessingSummary that = (ProcessingSummary) o;
        return totalFiles == that.totalFiles &&
                successfulFiles == that.successfulFiles &&
                partialFiles == that.partialFiles &&
                failedFiles == that.failedFiles &&
                totalMessages == that.totalMessages &&
                validMessages == that.validMessages &&
                invalidMessages == that.invalidMessages &&
                skippedMessages == that.skippedMessages &&
                totalIssues == that.totalIssues;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalFiles, successfulFiles, partialFiles, failedFiles,
                totalMessages, validMessages, invalidMessages, skippedMessages, totalIssues);
    }

    @Override
    public String toString() {
        return "ProcessingSummary{" +
                "totalFiles=" + totalFiles +
                ", successfulFiles=" + successfulFiles +
                ", partialFiles=" + partialFiles +
                ", failedFiles=" + failedFiles +
                ", totalMessages=" + totalMessages +
                ", validMessages=" + validMessages +
                ", invalidMessages=" + invalidMessages +
                ", skippedMessages=" + skippedMessages +
                ", totalIssues=" + totalIssues +
                '}';
    }
}
