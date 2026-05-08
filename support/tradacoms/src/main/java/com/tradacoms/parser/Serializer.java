package com.tradacoms.parser;

import com.tradacoms.parser.model.Element;
import com.tradacoms.parser.model.Group;
import com.tradacoms.parser.model.Segment;
import com.tradacoms.parser.model.SegmentOrGroup;

import java.util.List;
import java.util.Objects;

/**
 * Serializer for converting TRADACOMS model objects to EDI string format.
 * Handles escaping of special characters using the release character.
 */
public final class Serializer {

    private final WriterConfig config;

    public Serializer() {
        this(WriterConfig.defaults());
    }

    public Serializer(WriterConfig config) {
        this.config = config != null ? config : WriterConfig.defaults();
    }

    /**
     * Escapes special characters in a string using the release character.
     * Special characters that need escaping: ' + : = ?
     *
     * @param value the string to escape
     * @return the escaped string
     */
    public String escape(String value) {
        if (value == null || value.isEmpty()) {
            return value != null ? value : "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 10);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (config.isSpecialCharacter(c)) {
                sb.append(config.getReleaseCharacter());
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Serializes an Element to TRADACOMS format.
     * Components are separated by the component separator (:).
     *
     * @param element the element to serialize
     * @return the serialized element string
     */
    public String serializeElement(Element element) {
        Objects.requireNonNull(element, "element must not be null");
        
        List<String> components = element.components();
        if (components.isEmpty()) {
            return "";
        }
        
        if (components.size() == 1) {
            return escape(components.get(0));
        }
        
        // Composite element with multiple components
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < components.size(); i++) {
            if (i > 0) {
                sb.append(config.getComponentSeparator());
            }
            sb.append(escape(components.get(i)));
        }
        return sb.toString();
    }

    /**
     * Serializes a Segment to TRADACOMS format.
     * Format: TAG=element1+element2+element3'
     *
     * @param segment the segment to serialize
     * @return the serialized segment string
     */
    public String serializeSegment(Segment segment) {
        Objects.requireNonNull(segment, "segment must not be null");
        
        StringBuilder sb = new StringBuilder();
        sb.append(segment.tag());
        sb.append(config.getTagSeparator());
        
        List<Element> elements = segment.elements();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(config.getElementSeparator());
            }
            sb.append(serializeElement(elements.get(i)));
        }
        
        sb.append(config.getSegmentTerminator());
        return sb.toString();
    }

    /**
     * Serializes a SegmentOrGroup to TRADACOMS format.
     * Groups are serialized by serializing all their content in order.
     *
     * @param item the segment or group to serialize
     * @return the serialized string
     */
    public String serializeSegmentOrGroup(SegmentOrGroup item) {
        Objects.requireNonNull(item, "item must not be null");
        
        if (item instanceof Segment segment) {
            return serializeSegment(segment);
        } else if (item instanceof Group group) {
            return serializeGroup(group);
        }
        throw new IllegalArgumentException("Unknown SegmentOrGroup type: " + item.getClass());
    }

    /**
     * Serializes a Group to TRADACOMS format.
     * Groups are serialized by serializing all their content (segments and nested groups) in order.
     *
     * @param group the group to serialize
     * @return the serialized group string
     */
    public String serializeGroup(Group group) {
        Objects.requireNonNull(group, "group must not be null");
        
        StringBuilder sb = new StringBuilder();
        for (SegmentOrGroup item : group.getContent()) {
            sb.append(serializeSegmentOrGroup(item));
        }
        return sb.toString();
    }

    /**
     * Serializes a list of SegmentOrGroup items to TRADACOMS format.
     *
     * @param content the list of segments and groups to serialize
     * @return the serialized string
     */
    public String serializeContent(List<SegmentOrGroup> content) {
        Objects.requireNonNull(content, "content must not be null");
        
        StringBuilder sb = new StringBuilder();
        for (SegmentOrGroup item : content) {
            sb.append(serializeSegmentOrGroup(item));
        }
        return sb.toString();
    }

    /**
     * Returns the configuration used by this serializer.
     */
    public WriterConfig getConfig() {
        return config;
    }
}
