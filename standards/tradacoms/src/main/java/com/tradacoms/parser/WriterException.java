package com.tradacoms.parser;

/**
 * Exception thrown when writing TRADACOMS content fails.
 * Contains context about the segment being written when the error occurred.
 * 
 * <p>Extends {@link EdiException} to provide a consistent exception hierarchy.
 * 
 * <p>Requirements: 10.5, 10.6
 */
public class WriterException extends EdiException {

    private final String segmentTag;

    /**
     * Creates a WriterException with just a message.
     */
    public WriterException(String message) {
        this(message, null, null, null);
    }

    /**
     * Creates a WriterException with a message and cause.
     */
    public WriterException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    /**
     * Creates a WriterException with a message and segment tag.
     */
    public WriterException(String message, String segmentTag) {
        this(message, null, segmentTag, null);
    }

    /**
     * Creates a WriterException with a message, error code, and segment tag.
     */
    public WriterException(String message, String errorCode, String segmentTag) {
        this(message, errorCode, segmentTag, null);
    }

    /**
     * Creates a WriterException with all parameters.
     */
    public WriterException(String message, String errorCode, String segmentTag, Throwable cause) {
        super(message, errorCode, buildContext(segmentTag), cause);
        this.segmentTag = segmentTag;
    }

    /**
     * Builds an ErrorContext from the segment tag.
     */
    private static ErrorContext buildContext(String segmentTag) {
        if (segmentTag == null) {
            return ErrorContext.empty();
        }
        return ErrorContext.builder()
                .segmentTag(segmentTag)
                .build();
    }

    /**
     * Returns the segment tag that was being written when the error occurred.
     * 
     * @return the segment tag, or null if not applicable
     */
    public String getSegmentTag() {
        return segmentTag;
    }

    /**
     * Returns true if this exception has segment tag information.
     */
    public boolean hasSegmentTag() {
        return segmentTag != null && !segmentTag.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WriterException{");
        sb.append("message='").append(getMessage()).append("'");
        if (getErrorCode() != null) {
            sb.append(", errorCode='").append(getErrorCode()).append("'");
        }
        if (segmentTag != null) {
            sb.append(", segmentTag='").append(segmentTag).append("'");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builder for creating WriterException with detailed context.
     */
    public static Builder builder(String message) {
        return new Builder(message);
    }

    public static final class Builder {
        private final String message;
        private String errorCode;
        private String segmentTag;
        private Throwable cause;

        private Builder(String message) {
            this.message = message;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder segmentTag(String segmentTag) {
            this.segmentTag = segmentTag;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public WriterException build() {
            return new WriterException(message, errorCode, segmentTag, cause);
        }
    }
}
