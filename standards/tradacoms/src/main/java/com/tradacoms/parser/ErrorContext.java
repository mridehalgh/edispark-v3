package com.tradacoms.parser;

import java.util.Objects;

/**
 * Context information for EDI processing errors.
 * Provides detailed location and content information for error diagnosis.
 */
public final class ErrorContext {

    private final String filename;
    private final int messageIndex;
    private final String segmentTag;
    private final int elementPosition;
    private final int componentPosition;
    private final int lineNumber;
    private final int charPosition;
    private final String rawSnippet;

    private ErrorContext(Builder builder) {
        this.filename = builder.filename;
        this.messageIndex = builder.messageIndex;
        this.segmentTag = builder.segmentTag;
        this.elementPosition = builder.elementPosition;
        this.componentPosition = builder.componentPosition;
        this.lineNumber = builder.lineNumber;
        this.charPosition = builder.charPosition;
        this.rawSnippet = builder.rawSnippet;
    }

    /**
     * Creates a new builder for ErrorContext.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an empty error context.
     */
    public static ErrorContext empty() {
        return builder().build();
    }

    /**
     * Creates an error context with just a filename.
     */
    public static ErrorContext forFile(String filename) {
        return builder().filename(filename).build();
    }

    /**
     * Creates an error context for a specific segment.
     */
    public static ErrorContext forSegment(String segmentTag, int lineNumber, String rawSnippet) {
        return builder()
                .segmentTag(segmentTag)
                .lineNumber(lineNumber)
                .rawSnippet(rawSnippet)
                .build();
    }

    public String getFilename() {
        return filename;
    }

    public int getMessageIndex() {
        return messageIndex;
    }

    public String getSegmentTag() {
        return segmentTag;
    }

    public int getElementPosition() {
        return elementPosition;
    }

    public int getComponentPosition() {
        return componentPosition;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getCharPosition() {
        return charPosition;
    }

    public String getRawSnippet() {
        return rawSnippet;
    }

    /**
     * Returns true if this context has file information.
     */
    public boolean hasFilename() {
        return filename != null && !filename.isEmpty();
    }

    /**
     * Returns true if this context has message index information.
     */
    public boolean hasMessageIndex() {
        return messageIndex >= 0;
    }

    /**
     * Returns true if this context has segment information.
     */
    public boolean hasSegmentTag() {
        return segmentTag != null && !segmentTag.isEmpty();
    }

    /**
     * Returns true if this context has element position information.
     */
    public boolean hasElementPosition() {
        return elementPosition >= 0;
    }

    /**
     * Returns true if this context has component position information.
     */
    public boolean hasComponentPosition() {
        return componentPosition >= 0;
    }

    /**
     * Returns true if this context has line number information.
     */
    public boolean hasLineNumber() {
        return lineNumber >= 0;
    }

    /**
     * Returns true if this context has character position information.
     */
    public boolean hasCharPosition() {
        return charPosition >= 0;
    }

    /**
     * Returns true if this context has a raw snippet.
     */
    public boolean hasRawSnippet() {
        return rawSnippet != null && !rawSnippet.isEmpty();
    }

    /**
     * Returns a human-readable string representation of this context.
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();

        if (hasFilename()) {
            sb.append("file: ").append(filename);
        }

        if (hasMessageIndex()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("message: ").append(messageIndex);
        }

        if (hasSegmentTag()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("segment: ").append(segmentTag);
        }

        if (hasElementPosition()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("element: ").append(elementPosition);
        }

        if (hasComponentPosition()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("component: ").append(componentPosition);
        }

        if (hasLineNumber()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("line: ").append(lineNumber);
        }

        if (hasCharPosition()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("char: ").append(charPosition);
        }

        if (hasRawSnippet()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("snippet: '").append(truncateSnippet(rawSnippet, 50)).append("'");
        }

        return sb.isEmpty() ? "unknown location" : sb.toString();
    }

    private String truncateSnippet(String snippet, int maxLength) {
        if (snippet.length() <= maxLength) {
            return snippet;
        }
        return snippet.substring(0, maxLength - 3) + "...";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErrorContext that = (ErrorContext) o;
        return messageIndex == that.messageIndex &&
                elementPosition == that.elementPosition &&
                componentPosition == that.componentPosition &&
                lineNumber == that.lineNumber &&
                charPosition == that.charPosition &&
                Objects.equals(filename, that.filename) &&
                Objects.equals(segmentTag, that.segmentTag) &&
                Objects.equals(rawSnippet, that.rawSnippet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename, messageIndex, segmentTag, elementPosition,
                componentPosition, lineNumber, charPosition, rawSnippet);
    }

    @Override
    public String toString() {
        return "ErrorContext{" + toDisplayString() + "}";
    }

    /**
     * Builder for creating ErrorContext instances.
     */
    public static final class Builder {
        private String filename;
        private int messageIndex = -1;
        private String segmentTag;
        private int elementPosition = -1;
        private int componentPosition = -1;
        private int lineNumber = -1;
        private int charPosition = -1;
        private String rawSnippet;

        private Builder() {}

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder messageIndex(int messageIndex) {
            this.messageIndex = messageIndex;
            return this;
        }

        public Builder segmentTag(String segmentTag) {
            this.segmentTag = segmentTag;
            return this;
        }

        public Builder elementPosition(int elementPosition) {
            this.elementPosition = elementPosition;
            return this;
        }

        public Builder componentPosition(int componentPosition) {
            this.componentPosition = componentPosition;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder charPosition(int charPosition) {
            this.charPosition = charPosition;
            return this;
        }

        public Builder rawSnippet(String rawSnippet) {
            this.rawSnippet = rawSnippet;
            return this;
        }

        public ErrorContext build() {
            return new ErrorContext(this);
        }
    }
}
