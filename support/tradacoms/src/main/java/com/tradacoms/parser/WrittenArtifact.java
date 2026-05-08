package com.tradacoms.parser;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Record representing a written output artifact from a split operation.
 * Contains metadata about the written file including path, correlation ID,
 * message IDs, and byte count.
 *
 * @param path the path to the written file (may be null for callback targets)
 * @param correlationId unique identifier for this artifact
 * @param messageIds list of message IDs included in this artifact
 * @param byteCount number of bytes written
 * @param content the serialized content (for callback targets)
 */
public record WrittenArtifact(
        Path path,
        String correlationId,
        List<String> messageIds,
        long byteCount,
        String content
) {
    public WrittenArtifact {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        messageIds = messageIds != null ? List.copyOf(messageIds) : List.of();
    }

    /**
     * Creates a WrittenArtifact for a file-based output.
     *
     * @param path the path to the written file
     * @param correlationId unique identifier
     * @param messageIds list of message IDs
     * @param byteCount number of bytes written
     * @return a new WrittenArtifact
     */
    public static WrittenArtifact ofFile(Path path, String correlationId, List<String> messageIds, long byteCount) {
        return new WrittenArtifact(path, correlationId, messageIds, byteCount, null);
    }

    /**
     * Creates a WrittenArtifact for a callback-based output.
     *
     * @param correlationId unique identifier
     * @param messageIds list of message IDs
     * @param content the serialized content
     * @return a new WrittenArtifact
     */
    public static WrittenArtifact ofContent(String correlationId, List<String> messageIds, String content) {
        return new WrittenArtifact(null, correlationId, messageIds, 
                content != null ? content.length() : 0, content);
    }

    /**
     * Returns the number of messages in this artifact.
     */
    public int messageCount() {
        return messageIds.size();
    }
}
