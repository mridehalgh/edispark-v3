package com.tradacoms.parser;

import java.util.Objects;

/**
 * Template for generating filenames when splitting batches.
 * Supports placeholders that are replaced with actual values:
 * - {groupKey} - the grouping key from the split strategy
 * - {index} - the output file index (0-based)
 * - {messageType} - the message type (for ByMessageType strategy)
 * - {batchId} - the original batch ID
 */
public final class FilenameTemplate {

    private static final String DEFAULT_PATTERN = "batch_{groupKey}_{index}.edi";
    
    private final String pattern;
    private final String extension;

    private FilenameTemplate(String pattern, String extension) {
        this.pattern = pattern;
        this.extension = extension;
    }

    /**
     * Creates a filename template with the given pattern.
     *
     * @param pattern the filename pattern with placeholders
     * @return a new FilenameTemplate
     */
    public static FilenameTemplate of(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        return new FilenameTemplate(pattern, ".edi");
    }

    /**
     * Creates a filename template with the given pattern and extension.
     *
     * @param pattern the filename pattern with placeholders
     * @param extension the file extension (including the dot)
     * @return a new FilenameTemplate
     */
    public static FilenameTemplate of(String pattern, String extension) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(extension, "extension must not be null");
        return new FilenameTemplate(pattern, extension);
    }

    /**
     * Returns the default filename template.
     *
     * @return the default template
     */
    public static FilenameTemplate defaultTemplate() {
        return new FilenameTemplate(DEFAULT_PATTERN, ".edi");
    }

    /**
     * Generates a filename by replacing placeholders with actual values.
     *
     * @param groupKey the grouping key
     * @param index the output file index
     * @param messageType the message type (may be null)
     * @param batchId the batch ID (may be null)
     * @return the generated filename
     */
    public String generate(String groupKey, int index, String messageType, String batchId) {
        String result = pattern;
        result = result.replace("{groupKey}", sanitize(groupKey != null ? groupKey : "unknown"));
        result = result.replace("{index}", String.valueOf(index));
        result = result.replace("{messageType}", sanitize(messageType != null ? messageType : "unknown"));
        result = result.replace("{batchId}", sanitize(batchId != null ? batchId : "batch"));
        return result;
    }

    /**
     * Sanitizes a string for use in a filename by removing/replacing invalid characters.
     */
    private String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "unknown";
        }
        // Replace characters that are invalid in filenames
        return value.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public String getPattern() {
        return pattern;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public String toString() {
        return "FilenameTemplate{pattern='" + pattern + "'}";
    }
}
