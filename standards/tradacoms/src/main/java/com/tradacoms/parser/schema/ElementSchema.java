package com.tradacoms.parser.schema;

import java.util.List;
import java.util.Objects;

/**
 * Schema for an element (data field) within a segment.
 * Elements may be simple values or composites with sub-components.
 * 
 * Example from CREDIT.json:
 * "MSRF": {
 *   "id": "MSRF",
 *   "name": "Message Reference",
 *   "usage": "M",
 *   "type": "int",
 *   "minLength": 1,
 *   "maxLength": 12
 * }
 * 
 * Composite example:
 * "TYPE": {
 *   "id": "TYPE",
 *   "name": "Type of Message",
 *   "usage": "M",
 *   "values": [ { component definitions } ]
 * }
 */
public final class ElementSchema {

    private final String id;
    private final String name;
    private final String slug;
    private final String usage;
    private final String type;
    private final Integer length;
    private final Integer minLength;
    private final Integer maxLength;
    private final List<ComponentSchema> values;

    public ElementSchema(
            String id,
            String name,
            String slug,
            String usage,
            String type,
            Integer length,
            Integer minLength,
            Integer maxLength,
            List<ComponentSchema> values
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.usage = usage;
        this.type = type;
        this.length = length;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.values = values != null ? List.copyOf(values) : List.of();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getUsage() {
        return usage;
    }

    public String getType() {
        return type;
    }

    public Integer getLength() {
        return length;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public List<ComponentSchema> getValues() {
        return values;
    }

    /**
     * Returns true if this element is a composite (has sub-components).
     */
    public boolean isComposite() {
        return !values.isEmpty();
    }

    /**
     * Returns true if this element is mandatory (usage = "M").
     */
    public boolean isMandatory() {
        return "M".equals(usage);
    }

    /**
     * Returns the data type enum for this element.
     */
    public DataType getDataType() {
        return DataType.fromSchemaValue(type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementSchema that = (ElementSchema) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(slug, that.slug) &&
                Objects.equals(usage, that.usage) &&
                Objects.equals(type, that.type) &&
                Objects.equals(length, that.length) &&
                Objects.equals(minLength, that.minLength) &&
                Objects.equals(maxLength, that.maxLength) &&
                Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, slug, usage, type, length, minLength, maxLength, values);
    }

    @Override
    public String toString() {
        return "ElementSchema{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", usage='" + usage + '\'' +
                ", type='" + type + '\'' +
                ", isComposite=" + isComposite() +
                '}';
    }
}
