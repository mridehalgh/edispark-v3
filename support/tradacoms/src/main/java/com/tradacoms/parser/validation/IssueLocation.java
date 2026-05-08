package com.tradacoms.parser.validation;

import java.util.Objects;

/**
 * Location information for a validation issue.
 * Provides context about where in the EDI content the issue occurred.
 */
public record IssueLocation(
        String filename,
        int messageIndex,
        String segmentTag,
        int elementPosition,
        int componentPosition,
        int lineNumber
) {

    /**
     * Creates a location with all fields.
     */
    public IssueLocation {
        // Allow null filename for in-memory validation
        // Use -1 to indicate "not applicable" for numeric fields
    }

    /**
     * Creates a batch-level location (no message context).
     */
    public static IssueLocation batchLevel(String filename) {
        return new IssueLocation(filename, -1, null, -1, -1, -1);
    }

    /**
     * Creates a message-level location.
     */
    public static IssueLocation messageLevel(String filename, int messageIndex) {
        return new IssueLocation(filename, messageIndex, null, -1, -1, -1);
    }

    /**
     * Creates a segment-level location.
     */
    public static IssueLocation segmentLevel(String filename, int messageIndex, String segmentTag, int lineNumber) {
        return new IssueLocation(filename, messageIndex, segmentTag, -1, -1, lineNumber);
    }

    /**
     * Creates an element-level location.
     */
    public static IssueLocation elementLevel(
            String filename,
            int messageIndex,
            String segmentTag,
            int elementPosition,
            int lineNumber
    ) {
        return new IssueLocation(filename, messageIndex, segmentTag, elementPosition, -1, lineNumber);
    }

    /**
     * Creates a component-level location.
     */
    public static IssueLocation componentLevel(
            String filename,
            int messageIndex,
            String segmentTag,
            int elementPosition,
            int componentPosition,
            int lineNumber
    ) {
        return new IssueLocation(filename, messageIndex, segmentTag, elementPosition, componentPosition, lineNumber);
    }

    /**
     * Returns true if this location has file information.
     */
    public boolean hasFilename() {
        return filename != null && !filename.isEmpty();
    }

    /**
     * Returns true if this location has message context.
     */
    public boolean hasMessageContext() {
        return messageIndex >= 0;
    }

    /**
     * Returns true if this location has segment context.
     */
    public boolean hasSegmentContext() {
        return segmentTag != null && !segmentTag.isEmpty();
    }

    /**
     * Returns true if this location has element context.
     */
    public boolean hasElementContext() {
        return elementPosition >= 0;
    }

    /**
     * Returns true if this location has component context.
     */
    public boolean hasComponentContext() {
        return componentPosition >= 0;
    }

    /**
     * Returns true if this location has line number information.
     */
    public boolean hasLineNumber() {
        return lineNumber >= 0;
    }

    /**
     * Returns a human-readable string representation of this location.
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        
        if (hasFilename()) {
            sb.append(filename);
        }
        
        if (hasMessageContext()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("message ").append(messageIndex);
        }
        
        if (hasSegmentContext()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("segment ").append(segmentTag);
        }
        
        if (hasElementContext()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("element ").append(elementPosition);
        }
        
        if (hasComponentContext()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("component ").append(componentPosition);
        }
        
        if (hasLineNumber()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("line ").append(lineNumber);
        }
        
        return sb.isEmpty() ? "unknown location" : sb.toString();
    }

    @Override
    public String toString() {
        return "IssueLocation{" + toDisplayString() + "}";
    }
}
