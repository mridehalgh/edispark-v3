package com.tradacoms.parser.lexer;

/**
 * Sealed interface representing tokens produced by the TRADACOMS lexer.
 * Each token type captures specific syntactic elements of TRADACOMS EDI format.
 */
public sealed interface Token {

    /**
     * Marks the start of a segment with its tag.
     * @param tag The segment tag (e.g., "STX", "MHD", "CLO")
     * @param line The line number where the segment starts (1-based)
     * @param position The character position within the line (0-based)
     */
    record SegmentStart(String tag, int line, int position) implements Token {}

    /**
     * Represents an element value within a segment.
     * @param value The element value (may be empty string)
     * @param index The 0-based index of this element within the segment
     */
    record ElementValue(String value, int index) implements Token {}

    /**
     * Represents a component value within a composite element.
     * @param value The component value (may be empty string)
     * @param index The 0-based index of this component within the element
     */
    record ComponentValue(String value, int index) implements Token {}

    /**
     * Marks the end of a segment.
     * @param line The line number where the segment ends
     * @param position The character position of the terminator
     */
    record SegmentEnd(int line, int position) implements Token {}

    /**
     * Marks the end of input stream.
     */
    record EndOfInput() implements Token {}

    /**
     * Represents a lexer error.
     * @param message Description of the error
     * @param line The line number where the error occurred
     * @param position The character position where the error occurred
     * @param snippet Raw content snippet around the error
     */
    record Error(String message, int line, int position, String snippet) implements Token {}
}
