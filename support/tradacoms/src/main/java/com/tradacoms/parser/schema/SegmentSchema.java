package com.tradacoms.parser.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Schema for an individual segment within a message or group.
 * 
 * Example from CREDIT.json:
 * "MHD": {
 *   "name": "MESSAGE HEADER",
 *   "slug": "message_header_mhd",
 *   "position": "01",
 *   "usage": "M",
 *   "count": null,
 *   "id": "MHD",
 *   "values": { ... element definitions ... },
 *   "type": "SEGMENT"
 * }
 */
public final class SegmentSchema implements SegmentOrGroupSchema {

    private final String id;
    private final String name;
    private final String slug;
    private final String position;
    private final String usage;
    private final String count;
    private final Map<String, ElementSchema> values;

    public SegmentSchema(
            String id,
            String name,
            String slug,
            String position,
            String usage,
            String count,
            Map<String, ElementSchema> values
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.position = position;
        this.usage = usage;
        this.count = count;
        this.values = values != null ? new LinkedHashMap<>(values) : new LinkedHashMap<>();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getPosition() {
        return position;
    }

    @Override
    public String getUsage() {
        return usage;
    }

    public String getCount() {
        return count;
    }

    public Map<String, ElementSchema> getValues() {
        return Map.copyOf(values);
    }

    /**
     * Returns true if this segment is mandatory (usage = "M").
     */
    @Override
    public boolean isMandatory() {
        return "M".equals(usage);
    }

    /**
     * Returns the maximum number of occurrences.
     * Returns -1 for unbounded (">1"), 1 if count is null.
     */
    public int getMaxOccurs() {
        if (count == null) {
            return 1;
        }
        if (">1".equals(count)) {
            return -1; // unbounded
        }
        try {
            return Integer.parseInt(count);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Returns the element schema for the given element ID.
     */
    public Optional<ElementSchema> getElement(String elementId) {
        return Optional.ofNullable(values.get(elementId));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SegmentSchema that = (SegmentSchema) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(slug, that.slug) &&
                Objects.equals(position, that.position) &&
                Objects.equals(usage, that.usage) &&
                Objects.equals(count, that.count) &&
                Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, slug, position, usage, count, values);
    }

    @Override
    public String toString() {
        return "SegmentSchema{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", position='" + position + '\'' +
                ", usage='" + usage + '\'' +
                '}';
    }
}
