package com.tradacoms.parser.schema;

import java.util.Objects;

/**
 * Schema for a component (sub-element) within a composite element.
 * Components are the individual parts of a composite element, separated by ':' in TRADACOMS.
 */
public final class ComponentSchema {

    private final String name;
    private final String slug;
    private final String usage;
    private final String type;
    private final Integer length;
    private final Integer minLength;
    private final Integer maxLength;

    public ComponentSchema(
            String name,
            String slug,
            String usage,
            String type,
            Integer length,
            Integer minLength,
            Integer maxLength
    ) {
        this.name = name;
        this.slug = slug;
        this.usage = usage;
        this.type = type;
        this.length = length;
        this.minLength = minLength;
        this.maxLength = maxLength;
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

    /**
     * Returns true if this component is mandatory (usage = "M").
     */
    public boolean isMandatory() {
        return "M".equals(usage);
    }

    /**
     * Returns the data type enum for this component.
     */
    public DataType getDataType() {
        return DataType.fromSchemaValue(type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComponentSchema that = (ComponentSchema) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(slug, that.slug) &&
                Objects.equals(usage, that.usage) &&
                Objects.equals(type, that.type) &&
                Objects.equals(length, that.length) &&
                Objects.equals(minLength, that.minLength) &&
                Objects.equals(maxLength, that.maxLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, slug, usage, type, length, minLength, maxLength);
    }

    @Override
    public String toString() {
        return "ComponentSchema{" +
                "name='" + name + '\'' +
                ", usage='" + usage + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
