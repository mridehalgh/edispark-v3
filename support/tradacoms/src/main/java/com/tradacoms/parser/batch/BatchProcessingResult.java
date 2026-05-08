package com.tradacoms.parser.batch;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of batch processing operations.
 * Contains per-file results and a summary of the overall processing.
 */
public final class BatchProcessingResult {

    private final List<FileResult> fileResults;
    private final ProcessingSummary summary;

    public BatchProcessingResult(List<FileResult> fileResults, ProcessingSummary summary) {
        this.fileResults = fileResults != null ? List.copyOf(fileResults) : List.of();
        this.summary = Objects.requireNonNull(summary, "summary must not be null");
    }

    /**
     * Creates a result from a list of file results.
     * Summary is computed automatically.
     */
    public static BatchProcessingResult fromResults(List<FileResult> fileResults) {
        ProcessingSummary summary = ProcessingSummary.fromResults(fileResults);
        return new BatchProcessingResult(fileResults, summary);
    }

    /**
     * Returns the list of file results.
     */
    public List<FileResult> getFileResults() {
        return fileResults;
    }

    /**
     * Returns the processing summary.
     */
    public ProcessingSummary getSummary() {
        return summary;
    }

    /**
     * Returns the file result for the specified correlation ID.
     */
    public Optional<FileResult> getFileResult(String correlationId) {
        return fileResults.stream()
                .filter(fr -> correlationId.equals(fr.getCorrelationId()))
                .findFirst();
    }

    /**
     * Returns the number of files processed.
     */
    public int getFileCount() {
        return fileResults.size();
    }

    /**
     * Returns true if all files were processed successfully.
     */
    public boolean isAllSuccessful() {
        return summary.isAllSuccessful();
    }

    /**
     * Returns true if any files failed.
     */
    public boolean hasFailures() {
        return summary.hasFailures();
    }

    /**
     * Returns the total number of messages across all files.
     */
    public int getTotalMessageCount() {
        return summary.getTotalMessages();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchProcessingResult that = (BatchProcessingResult) o;
        return Objects.equals(fileResults, that.fileResults) &&
                Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileResults, summary);
    }

    @Override
    public String toString() {
        return "BatchProcessingResult{" +
                "fileCount=" + fileResults.size() +
                ", summary=" + summary +
                '}';
    }
}
