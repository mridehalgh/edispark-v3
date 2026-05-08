package com.tradacoms.parser.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a TRADACOMS interchange (batch) containing one or more messages.
 * A batch is bounded by STX (start) and END (end) envelope segments.
 */
public final class Batch {

    private final String batchId;
    private final String senderId;
    private final String receiverId;
    private final Instant creationTimestamp;
    private final List<Message> messages;
    private final Segment rawHeader;
    private final Segment rawTrailer;
    private final SourceInfo sourceInfo;

    public Batch(
            String batchId,
            String senderId,
            String receiverId,
            Instant creationTimestamp,
            List<Message> messages,
            Segment rawHeader,
            Segment rawTrailer,
            SourceInfo sourceInfo
    ) {
        this.batchId = batchId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.creationTimestamp = creationTimestamp;
        this.messages = messages != null ? List.copyOf(messages) : List.of();
        this.rawHeader = rawHeader;
        this.rawTrailer = rawTrailer;
        this.sourceInfo = sourceInfo;
    }

    /**
     * Creates a batch with minimal required fields.
     */
    public static Batch of(List<Message> messages) {
        return new Batch(null, null, null, null, messages, null, null, null);
    }

    /**
     * Creates a batch with sender and receiver IDs.
     */
    public static Batch of(String senderId, String receiverId, List<Message> messages) {
        return new Batch(null, senderId, receiverId, null, messages, null, null, null);
    }

    public String getBatchId() {
        return batchId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public Instant getCreationTimestamp() {
        return creationTimestamp;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public Segment getRawHeader() {
        return rawHeader;
    }

    public Segment getRawTrailer() {
        return rawTrailer;
    }

    public SourceInfo getSourceInfo() {
        return sourceInfo;
    }

    /**
     * Returns the number of messages in this batch.
     */
    public int messageCount() {
        return messages.size();
    }

    /**
     * Returns the message at the specified index.
     */
    public Optional<Message> getMessage(int index) {
        if (index < 0 || index >= messages.size()) {
            return Optional.empty();
        }
        return Optional.of(messages.get(index));
    }

    /**
     * Returns all messages of the specified type.
     */
    public List<Message> getMessagesByType(String messageType) {
        Objects.requireNonNull(messageType, "messageType must not be null");
        return messages.stream()
                .filter(m -> messageType.equals(m.getMessageType()))
                .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Batch batch = (Batch) o;
        return Objects.equals(batchId, batch.batchId) &&
                Objects.equals(senderId, batch.senderId) &&
                Objects.equals(receiverId, batch.receiverId) &&
                Objects.equals(creationTimestamp, batch.creationTimestamp) &&
                Objects.equals(messages, batch.messages) &&
                Objects.equals(rawHeader, batch.rawHeader) &&
                Objects.equals(rawTrailer, batch.rawTrailer) &&
                Objects.equals(sourceInfo, batch.sourceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, senderId, receiverId, creationTimestamp, messages, rawHeader, rawTrailer, sourceInfo);
    }

    @Override
    public String toString() {
        return "Batch{" +
                "batchId='" + batchId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", messageCount=" + messages.size() +
                '}';
    }
}
