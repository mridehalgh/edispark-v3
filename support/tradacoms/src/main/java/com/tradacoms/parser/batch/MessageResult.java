package com.tradacoms.parser.batch;

import com.tradacoms.parser.validation.ValidationIssue;

import java.util.List;
import java.util.Objects;

/**
 * Result of processing a single message within a batch file.
 * Contains the message identifier, status, and any validation issues.
 */
public final class MessageResult {

    private final String messageId;
    private final int messageIndex;
    private final String messageType;
    private final MessageStatus status;
    private final List<ValidationIssue> issues;

    public MessageResult(
            String messageId,
            int messageIndex,
            String messageType,
            MessageStatus status,
            List<ValidationIssue> issues
    ) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.messageIndex = messageIndex;
        this.messageType = messageType;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.issues = issues != null ? List.copyOf(issues) : List.of();
    }

    /**
     * Creates a valid message result with no issues.
     */
    public static MessageResult valid(String messageId, int messageIndex, String messageType) {
        return new MessageResult(messageId, messageIndex, messageType, MessageStatus.VALID, List.of());
    }

    /**
     * Creates an invalid message result with issues.
     */
    public static MessageResult invalid(String messageId, int messageIndex, String messageType, 
                                        List<ValidationIssue> issues) {
        return new MessageResult(messageId, messageIndex, messageType, MessageStatus.INVALID, issues);
    }

    /**
     * Creates a skipped message result.
     */
    public static MessageResult skipped(String messageId, int messageIndex, String messageType) {
        return new MessageResult(messageId, messageIndex, messageType, MessageStatus.SKIPPED, List.of());
    }

    /**
     * Derives a message ID from control reference and message index.
     * Format: {controlRef}:{index} or just {index} if no control ref.
     */
    public static String deriveMessageId(String controlRef, int messageIndex) {
        if (controlRef != null && !controlRef.isEmpty()) {
            return controlRef + ":" + messageIndex;
        }
        return String.valueOf(messageIndex);
    }

    /**
     * Returns the unique message identifier.
     * Derived from control reference and message index.
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Returns the message index within the batch file.
     */
    public int getMessageIndex() {
        return messageIndex;
    }

    /**
     * Returns the message type (e.g., ORDERS, INVOIC).
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * Returns the processing status of this message.
     */
    public MessageStatus getStatus() {
        return status;
    }

    /**
     * Returns the list of validation issues for this message.
     */
    public List<ValidationIssue> getIssues() {
        return issues;
    }

    /**
     * Returns true if this message is valid.
     */
    public boolean isValid() {
        return status == MessageStatus.VALID;
    }

    /**
     * Returns true if this message is invalid.
     */
    public boolean isInvalid() {
        return status == MessageStatus.INVALID;
    }

    /**
     * Returns true if this message was skipped.
     */
    public boolean isSkipped() {
        return status == MessageStatus.SKIPPED;
    }

    /**
     * Returns the number of issues.
     */
    public int getIssueCount() {
        return issues.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageResult that = (MessageResult) o;
        return messageIndex == that.messageIndex &&
                Objects.equals(messageId, that.messageId) &&
                Objects.equals(messageType, that.messageType) &&
                status == that.status &&
                Objects.equals(issues, that.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, messageIndex, messageType, status, issues);
    }

    @Override
    public String toString() {
        return "MessageResult{" +
                "messageId='" + messageId + '\'' +
                ", messageIndex=" + messageIndex +
                ", messageType='" + messageType + '\'' +
                ", status=" + status +
                ", issueCount=" + issues.size() +
                '}';
    }
}
