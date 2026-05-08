package com.tradacoms.parser.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a TRADACOMS segment - a line of EDI data containing related data elements.
 * Examples: STX (start of transmission), MHD (message header), CLO (customer location), OLD (order line detail)
 */
public record Segment(
        String tag,
        List<Element> elements,
        int lineNumber,
        int charPosition,
        String rawContent,
        boolean isGroupTrigger
) implements SegmentOrGroup {

    public Segment {
        Objects.requireNonNull(tag, "tag must not be null");
        Objects.requireNonNull(elements, "elements must not be null");
        elements = List.copyOf(elements);
        rawContent = rawContent != null ? rawContent : "";
    }

    /**
     * Creates a segment with minimal required fields.
     */
    public static Segment of(String tag, List<Element> elements) {
        return new Segment(tag, elements, 0, 0, "", false);
    }

    /**
     * Creates a segment marked as a group trigger.
     */
    public static Segment groupTrigger(String tag, List<Element> elements) {
        return new Segment(tag, elements, 0, 0, "", true);
    }

    /**
     * Returns the element at the specified index, or empty if out of bounds.
     */
    public Optional<Element> getElement(int index) {
        if (index < 0 || index >= elements.size()) {
            return Optional.empty();
        }
        return Optional.of(elements.get(index));
    }

    /**
     * Returns the value of the first component of the element at the specified index.
     */
    public String getElementValue(int index) {
        return getElement(index).map(Element::getValue).orElse("");
    }

    /**
     * Returns the number of elements in this segment.
     */
    public int elementCount() {
        return elements.size();
    }
}
