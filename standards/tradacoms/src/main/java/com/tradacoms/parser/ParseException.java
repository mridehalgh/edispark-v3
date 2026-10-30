package com.tradacoms.parser;

/**
 * Exception thrown when parsing TRADACOMS content fails.
 * Contains detailed context about where the error occurred including
 * line number, character position, segment tag, and raw content snippet.
 * 
 * <p>Extends {@link EdiException} to provide a consistent exception hierarchy.
 * 
 * <p>Requirements: 10.5, 10.6
 */
public class ParseException extends EdiException {

    private final int lineNumber;
    private final int charPosition;
    private final String rawSnippet;
    private final String segmentTag;
    private final int elementPosition;

    public ParseException(String message) {
        this(message, null, -1, -1, null, null, -1);
    }

    public ParseException(String message, Throwable cause) {
        super(message, null, null, cause);
        this.lineNumber = -1;
        this.charPosition = -1;
        this.rawSnippet = null;
        this.segmentTag = null;
        this.elementPosition = -1;
    }

    public ParseException(
            String message,
            String errorCode,
            int lineNumber,
            int charPosition,
            String rawSnippet,
            String segmentTag,
            int elementPosition
    ) {
        super(message, errorCode, buildContext(lineNumber, charPosition, rawSnippet, segmentTag, elementPosition));
        this.lineNumber = lineNumber;
        this.charPosition = charPosition;
        this.rawSnippet = rawSnippet;
        this.segmentTag = segmentTag;
        this.elementPosition = elementPosition;
    }

    /**
     * Creates an ErrorContext from the parse exception fields.
     */
    private static ErrorContext buildContext(
            int lineNumber,
            int charPosition,
            String rawSnippet,
            String segmentTag,
            int elementPosition
    ) {
        return ErrorContext.builder()
                .lineNumber(lineNumber)
                .charPosition(charPosition)
                .rawSnippet(rawSnippet)
                .segmentTag(segmentTag)
                .elementPosition(elementPosition)
                .build();
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

    public String getSegmentTag() {
        return segmentTag;
    }

    public int getElementPosition() {
        return elementPosition;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ParseException{");
        sb.append("message='").append(getMessage()).append('\'');
        if (getErrorCode() != null) {
            sb.append(", errorCode='").append(getErrorCode()).append('\'');
        }
        if (lineNumber >= 0) {
            sb.append(", lineNumber=").append(lineNumber);
        }
        if (charPosition >= 0) {
            sb.append(", charPosition=").append(charPosition);
        }
        if (segmentTag != null) {
            sb.append(", segmentTag='").append(segmentTag).append('\'');
        }
        if (elementPosition >= 0) {
            sb.append(", elementPosition=").append(elementPosition);
        }
        if (rawSnippet != null) {
            sb.append(", rawSnippet='").append(rawSnippet).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Builder for creating ParseException with detailed context.
     */
    public static Builder builder(String message) {
        return new Builder(message);
    }

    public static final class Builder {
        private final String message;
        private String errorCode;
        private int lineNumber = -1;
        private int charPosition = -1;
        private String rawSnippet;
        private String segmentTag;
        private int elementPosition = -1;

        private Builder(String message) {
            this.message = message;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
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

        public Builder segmentTag(String segmentTag) {
            this.segmentTag = segmentTag;
            return this;
        }

        public Builder elementPosition(int elementPosition) {
            this.elementPosition = elementPosition;
            return this;
        }

        public ParseException build() {
            return new ParseException(message, errorCode, lineNumber, charPosition, 
                    rawSnippet, segmentTag, elementPosition);
        }
    }
}
