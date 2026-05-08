package com.tradacoms.parser;

/**
 * Base exception class for all EDI processing errors.
 * Provides a common structure for error codes and context information.
 * 
 * <p>This is the root of the exception hierarchy for the TRADACOMS parser library:
 * <ul>
 *   <li>{@link ParseException} - thrown when parsing fails</li>
 *   <li>{@link ValidationException} - thrown when validation fails</li>
 *   <li>{@link WriterException} - thrown when writing fails</li>
 * </ul>
 * 
 * <p>Requirements: 10.5, 10.6
 */
public class EdiException extends RuntimeException {

    private final String errorCode;
    private final ErrorContext context;

    /**
     * Creates an EdiException with just a message.
     */
    public EdiException(String message) {
        this(message, null, null, null);
    }

    /**
     * Creates an EdiException with a message and cause.
     */
    public EdiException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    /**
     * Creates an EdiException with a message and error code.
     */
    public EdiException(String message, String errorCode) {
        this(message, errorCode, null, null);
    }

    /**
     * Creates an EdiException with a message, error code, and context.
     */
    public EdiException(String message, String errorCode, ErrorContext context) {
        this(message, errorCode, context, null);
    }

    /**
     * Creates an EdiException with all parameters.
     */
    public EdiException(String message, String errorCode, ErrorContext context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context;
    }

    /**
     * Returns the error code for this exception.
     * Error codes follow the pattern: CATEGORY_NNN (e.g., PARSE_001, VALID_002).
     * 
     * @return the error code, or null if not set
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the error context containing location and content information.
     * 
     * @return the error context, or null if not set
     */
    public ErrorContext getContext() {
        return context;
    }

    /**
     * Returns the category of this error based on its error code.
     * Categories include: SYNTAX, SCHEMA, ENVELOPE, BUSINESS, UNKNOWN.
     * 
     * @return the error category
     */
    public String getCategory() {
        if (errorCode == null) {
            return "UNKNOWN";
        }
        if (errorCode.startsWith("PARSE_")) {
            return "SYNTAX";
        }
        if (errorCode.startsWith("VALID_")) {
            return "SCHEMA";
        }
        if (errorCode.startsWith("ENV_")) {
            return "ENVELOPE";
        }
        if (errorCode.startsWith("BUS_")) {
            return "BUSINESS";
        }
        if (errorCode.startsWith("WRITE_")) {
            return "WRITER";
        }
        return "UNKNOWN";
    }

    /**
     * Returns true if this exception has an error code.
     */
    public boolean hasErrorCode() {
        return errorCode != null && !errorCode.isEmpty();
    }

    /**
     * Returns true if this exception has context information.
     */
    public boolean hasContext() {
        return context != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("{message='").append(getMessage()).append("'");
        if (errorCode != null) {
            sb.append(", errorCode='").append(errorCode).append("'");
        }
        if (context != null) {
            sb.append(", context=").append(context.toDisplayString());
        }
        sb.append("}");
        return sb.toString();
    }
}
