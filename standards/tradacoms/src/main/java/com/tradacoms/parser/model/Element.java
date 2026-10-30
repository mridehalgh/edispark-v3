package com.tradacoms.parser.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a data element within a TRADACOMS segment.
 * Elements may contain multiple components (sub-elements) separated by ':'.
 */
public record Element(List<String> components, boolean isRepeating) {

    public Element {
        Objects.requireNonNull(components, "components must not be null");
        components = List.copyOf(components);
    }

    /**
     * Creates a simple element with a single component.
     */
    public static Element of(String value) {
        return new Element(List.of(value != null ? value : ""), false);
    }

    /**
     * Creates an element with multiple components.
     */
    public static Element of(List<String> components) {
        return new Element(components, false);
    }

    /**
     * Creates a repeating element with multiple components.
     */
    public static Element repeating(List<String> components) {
        return new Element(components, true);
    }

    /**
     * Returns the first component value, or empty string if no components.
     */
    public String getValue() {
        return components.isEmpty() ? "" : components.get(0);
    }

    /**
     * Returns the component at the specified index, or empty string if out of bounds.
     */
    public String getComponent(int index) {
        if (index < 0 || index >= components.size()) {
            return "";
        }
        return components.get(index);
    }

    /**
     * Returns the number of components in this element.
     */
    public int componentCount() {
        return components.size();
    }

    /**
     * Returns true if this element has multiple components.
     */
    public boolean isComposite() {
        return components.size() > 1;
    }
}
