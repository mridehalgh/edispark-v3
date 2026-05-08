package com.tradacoms.parser.batch;

import com.tradacoms.parser.validation.ValidationIssue;

import java.util.List;
import java.util.Objects;

/**
 * Result of processing a single file in batch operations.
 * Contains the correlation ID, filename, status, message results, and file-level issues.
 */
public final class FileResult {

    private final String correlationId;
    private final String filename;
    private final FileStatus status;
    private final List<MessageResult> messageResults;
    private final List<ValidationIssue> fileIssues;

    public FileResult(
            String correlationId,
            String filename,
            FileStatus status,
            List<MessageResult> messageResults,
            List<ValidationIssue> fileIssues
    ) {
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.filename = filename;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.messageResults = messageResults != null ? List.copyOf(messageResults) : List.of();
        this.fileIssues = fileIssues != null ? List.copyOf(fileIssues) : List.of();
    }

    /**
     * Creates a successful file result.
     */
    public static FileResult success(String correlationId, String filename, 
                                     List<MessageResult> messageResults) {
        return new FileResult(correlationId, filename, FileStatus.SUCCESS, messageResults, List.of());
    }

    /**
     * Creates a partial file result (some messages had issues).
     */
    public static FileResult partial(String correlationId, String filename,
                                     List<MessageResult> messageResults,
                                     List<ValidationIssue> fileIssues) {
        return new FileResult(correlationId, filename, FileStatus.PARTIAL, messageResults, fileIssues);
    }

    /**
     * Creates a failed file result.
     */
    public static FileResult failed(String correlationId, String filename,
                                    List<ValidationIssue> fileIssues) {
        return new FileResult(correlationId, filename, FileStatus.FAILED, List.of(), fileIssues);
    }

    /**
     * Creates a failed file result from an exception.
     */
    public static FileResult failed(String correlationId, String filename, Exception e) {
        ValidationIssue issue = ValidationIssue.error(
                "FILE_001",
                "File processing failed: " + e.getMessage(),
                null
        );
        return new FileResult(correlationId, filename, FileStatus.FAILED, List.of(), List.of(issue));
    }

    /**
     * Returns the correlation ID for this file.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Returns the filename.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Returns the processing status of this file.
     */
    public FileStatus getStatus() {
        return status;
    }

    /**
     * Returns the list of message results.
     */
    public List<MessageResult> getMessageResults() {
        return messageResults;
    }

    /**
     * Returns the list of file-level issues.
     */
    public List<ValidationIssue> getFileIssues() {
        return fileIssues;
    }

    /**
     * Returns true if this file was processed successfully.
     */
    public boolean isSuccess() {
        return status == FileStatus.SUCCESS;
    }

    /**
     * Returns true if this file had partial success.
     */
    public boolean isPartial() {
        return status == FileStatus.PARTIAL;
    }

    /**
     * Returns true if this file processing failed.
     */
    public boolean isFailed() {
        return status == FileStatus.FAILED;
    }

    /**
     * Returns the total number of messages processed.
     */
    public int getMessageCount() {
        return messageResults.size();
    }

    /**
     * Returns the number of valid messages.
     */
    public int getValidMessageCount() {
        return (int) messageResults.stream()
                .filter(MessageResult::isValid)
                .count();
    }

    /**
     * Returns the number of invalid messages.
     */
    public int getInvalidMessageCount() {
        return (int) messageResults.stream()
                .filter(MessageResult::isInvalid)
                .count();
    }

    /**
     * Returns the number of skipped messages.
     */
    public int getSkippedMessageCount() {
        return (int) messageResults.stream()
                .filter(MessageResult::isSkipped)
                .count();
    }

    /**
     * Returns the total number of issues (file-level + message-level).
     */
    public int getTotalIssueCount() {
        int total = fileIssues.size();
        for (MessageResult mr : messageResults) {
            total += mr.getIssueCount();
        }
        return total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileResult that = (FileResult) o;
        return Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(filename, that.filename) &&
                status == that.status &&
                Objects.equals(messageResults, that.messageResults) &&
                Objects.equals(fileIssues, that.fileIssues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationId, filename, status, messageResults, fileIssues);
    }

    @Override
    public String toString() {
        return "FileResult{" +
                "correlationId='" + correlationId + '\'' +
                ", filename='" + filename + '\'' +
                ", status=" + status +
                ", messageCount=" + messageResults.size() +
                ", issueCount=" + getTotalIssueCount() +
                '}';
    }
}
