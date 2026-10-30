package com.tradacoms.parser;

/**
 * Constants defining TRADACOMS EDI syntax characters and standard segment tags.
 */
public final class TradacomsSyntax {

    private TradacomsSyntax() {
        // Utility class - prevent instantiation
    }

    // ========== Syntax Characters ==========

    /**
     * Segment terminator character - marks the end of a segment.
     * Default: ' (single quote)
     */
    public static final char SEGMENT_TERMINATOR = '\'';

    /**
     * Element separator character - separates data elements within a segment.
     * Default: + (plus sign)
     */
    public static final char ELEMENT_SEPARATOR = '+';

    /**
     * Component separator character - separates sub-elements within a composite element.
     * Default: : (colon)
     */
    public static final char COMPONENT_SEPARATOR = ':';

    /**
     * Tag separator character - separates the segment tag from its data elements.
     * Default: = (equals sign)
     */
    public static final char TAG_SEPARATOR = '=';

    /**
     * Release character - escapes special characters within data.
     * Default: ? (question mark)
     */
    public static final char RELEASE_CHARACTER = '?';

    // ========== Standard Segment Tags ==========

    /**
     * Start of Transmission - envelope header segment.
     * Contains sender/receiver IDs, transmission reference, and timestamp.
     */
    public static final String STX = "STX";

    /**
     * End of Transmission - envelope trailer segment.
     * Contains message count and control totals.
     */
    public static final String END = "END";

    /**
     * Message Header - marks the start of a message within the transmission.
     * Contains message type and reference number.
     */
    public static final String MHD = "MHD";

    /**
     * Message Trailer - marks the end of a message.
     * Contains segment count for the message.
     */
    public static final String MTR = "MTR";

    // ========== Common Message Segment Tags ==========

    /**
     * Customer Location - identifies the customer/delivery location.
     */
    public static final String CLO = "CLO";

    /**
     * Order Line Detail - contains order line item information.
     */
    public static final String OLD = "OLD";

    /**
     * Order Detail Description - additional line item description.
     */
    public static final String ODD = "ODD";

    /**
     * Credit Line Detail - contains credit note line item information.
     */
    public static final String CLD = "CLD";

    /**
     * Delivery Note Address - delivery address information.
     */
    public static final String DNA = "DNA";

    // ========== Utility Methods ==========

    /**
     * Checks if the given character is a TRADACOMS special character that needs escaping.
     */
    public static boolean isSpecialCharacter(char c) {
        return c == SEGMENT_TERMINATOR ||
                c == ELEMENT_SEPARATOR ||
                c == COMPONENT_SEPARATOR ||
                c == TAG_SEPARATOR ||
                c == RELEASE_CHARACTER;
    }

    /**
     * Checks if the given segment tag is an envelope segment (STX or END).
     */
    public static boolean isEnvelopeSegment(String tag) {
        return STX.equals(tag) || END.equals(tag);
    }

    /**
     * Checks if the given segment tag is a message boundary segment (MHD or MTR).
     */
    public static boolean isMessageBoundarySegment(String tag) {
        return MHD.equals(tag) || MTR.equals(tag);
    }
}
