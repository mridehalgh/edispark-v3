package com.tradacoms.parser.validation;

/**
 * Error codes for different failure categories in TRADACOMS validation.
 * Codes are organized by category: PARSE (syntax), VALID (schema), ENV (envelope), BUS (business).
 */
public final class ErrorCode {

    private ErrorCode() {
        // Utility class
    }

    // ========== Syntax/Parse Errors (PARSE_xxx) ==========
    
    /** Invalid segment terminator */
    public static final String PARSE_001 = "PARSE_001";
    
    /** Malformed segment structure */
    public static final String PARSE_002 = "PARSE_002";
    
    /** Invalid escape sequence */
    public static final String PARSE_003 = "PARSE_003";
    
    /** Unexpected end of input */
    public static final String PARSE_004 = "PARSE_004";
    
    /** Invalid character encoding */
    public static final String PARSE_005 = "PARSE_005";

    // ========== Schema Validation Errors (VALID_xxx) ==========
    
    /** Missing required segment */
    public static final String VALID_001 = "VALID_001";
    
    /** Invalid segment order */
    public static final String VALID_002 = "VALID_002";
    
    /** Invalid element value */
    public static final String VALID_003 = "VALID_003";
    
    /** Element exceeds max length */
    public static final String VALID_004 = "VALID_004";
    
    /** Missing required element */
    public static final String VALID_005 = "VALID_005";
    
    /** Invalid data type */
    public static final String VALID_006 = "VALID_006";
    
    /** Group occurs too few times */
    public static final String VALID_007 = "VALID_007";
    
    /** Group occurs too many times */
    public static final String VALID_008 = "VALID_008";
    
    /** Unknown segment tag */
    public static final String VALID_009 = "VALID_009";
    
    /** Element below min length */
    public static final String VALID_010 = "VALID_010";

    // ========== Envelope Errors (ENV_xxx) ==========
    
    /** Missing STX segment */
    public static final String ENV_001 = "ENV_001";
    
    /** Missing END segment */
    public static final String ENV_002 = "ENV_002";
    
    /** Message count mismatch */
    public static final String ENV_003 = "ENV_003";
    
    /** Control reference mismatch */
    public static final String ENV_004 = "ENV_004";
    
    /** Missing MHD segment */
    public static final String ENV_005 = "ENV_005";
    
    /** Missing MTR segment */
    public static final String ENV_006 = "ENV_006";
    
    /** Segment count mismatch in MTR */
    public static final String ENV_007 = "ENV_007";

    // ========== Business Rule Errors (BUS_xxx) ==========
    
    /** Partner rule violation */
    public static final String BUS_001 = "BUS_001";
    
    /** Invalid routing key */
    public static final String BUS_002 = "BUS_002";

    // ========== Writer Errors (WRITE_xxx) ==========
    
    /** Failed to write segment */
    public static final String WRITE_001 = "WRITE_001";
    
    /** Invalid segment structure for writing */
    public static final String WRITE_002 = "WRITE_002";
    
    /** Failed to escape special characters */
    public static final String WRITE_003 = "WRITE_003";
    
    /** I/O error during write */
    public static final String WRITE_004 = "WRITE_004";
    
    /** Invalid batch structure */
    public static final String WRITE_005 = "WRITE_005";

    /**
     * Returns the category for the given error code.
     */
    public static String getCategory(String code) {
        if (code == null) {
            return "UNKNOWN";
        }
        if (code.startsWith("PARSE_")) {
            return "SYNTAX";
        }
        if (code.startsWith("VALID_")) {
            return "SCHEMA";
        }
        if (code.startsWith("ENV_")) {
            return "ENVELOPE";
        }
        if (code.startsWith("BUS_")) {
            return "BUSINESS";
        }
        if (code.startsWith("WRITE_")) {
            return "WRITER";
        }
        return "UNKNOWN";
    }

    /**
     * Returns a human-readable description for the given error code.
     */
    public static String getDescription(String code) {
        return switch (code) {
            case PARSE_001 -> "Invalid segment terminator";
            case PARSE_002 -> "Malformed segment structure";
            case PARSE_003 -> "Invalid escape sequence";
            case PARSE_004 -> "Unexpected end of input";
            case PARSE_005 -> "Invalid character encoding";
            case VALID_001 -> "Missing required segment";
            case VALID_002 -> "Invalid segment order";
            case VALID_003 -> "Invalid element value";
            case VALID_004 -> "Element exceeds max length";
            case VALID_005 -> "Missing required element";
            case VALID_006 -> "Invalid data type";
            case VALID_007 -> "Group occurs too few times";
            case VALID_008 -> "Group occurs too many times";
            case VALID_009 -> "Unknown segment tag";
            case VALID_010 -> "Element below min length";
            case ENV_001 -> "Missing STX segment";
            case ENV_002 -> "Missing END segment";
            case ENV_003 -> "Message count mismatch";
            case ENV_004 -> "Control reference mismatch";
            case ENV_005 -> "Missing MHD segment";
            case ENV_006 -> "Missing MTR segment";
            case ENV_007 -> "Segment count mismatch in MTR";
            case BUS_001 -> "Partner rule violation";
            case BUS_002 -> "Invalid routing key";
            case WRITE_001 -> "Failed to write segment";
            case WRITE_002 -> "Invalid segment structure for writing";
            case WRITE_003 -> "Failed to escape special characters";
            case WRITE_004 -> "I/O error during write";
            case WRITE_005 -> "Invalid batch structure";
            default -> "Unknown error";
        };
    }
}
