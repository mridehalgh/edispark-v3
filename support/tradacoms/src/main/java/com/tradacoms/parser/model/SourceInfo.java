package com.tradacoms.parser.model;

/**
 * Contains source location information for tracking the origin of parsed content.
 */
public record SourceInfo(
        String filename,
        long startOffset,
        long endOffset,
        String correlationId
) {

    /**
     * Creates a SourceInfo with just a filename.
     */
    public static SourceInfo of(String filename) {
        return new SourceInfo(filename, 0, 0, null);
    }

    /**
     * Creates a SourceInfo with filename and correlation ID.
     */
    public static SourceInfo of(String filename, String correlationId) {
        return new SourceInfo(filename, 0, 0, correlationId);
    }
}
