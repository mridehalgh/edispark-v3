package com.tradacoms.parser.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Schema for a group/loop structure.
 * Groups are identified by "groupId" in JSON and contain nested "segments".
 * 
 * Example from CREDIT.json:
 * "CLD": {
 *   "name": "CREDIT NOTE LINE DETAILS",
 *   "usage": "M",
 *   "count": ">1",
 *   "groupId": "CLD",
 *   "segments": { ... nested segments ... }
 * }
 */
public final class GroupSchema implements SegmentOrGroupSchema {

    private final String groupId;
    private final String name;
    private final String slug;
    private final String usage;
    private final String count;
    private final Map<String, SegmentOrGroupSchema> segments;

    public GroupSchema(
            String groupId,
            String name,
            String slug,
            String usage,
            String count,
            Map<String, SegmentOrGroupSchema> segments
    ) {
        this.groupId = groupId;
        this.name = name;
        this.slug = slug;
        this.usage = usage;
        this.count = count;
        this.segments = segments != null ? new LinkedHashMap<>(segments) : new LinkedHashMap<>();
    }

    @Override
    public String getId() {
        return groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    @Override
    public String getUsage() {
        return usage;
    }

    public String getCount() {
        return count;
    }

    public Map<String, SegmentOrGroupSchema> getSegments() {
        return Map.copyOf(segments);
    }

    /**
     * Returns true if this group is mandatory (usage = "M").
     */
    @Override
    public boolean isMandatory() {
        return "M".equals(usage);
    }

    /**
     * Returns the minimum number of occurrences.
     * Returns 1 if mandatory, 0 if optional.
     */
    public int getMinOccurs() {
        return isMandatory() ? 1 : 0;
    }

    /**
     * Returns the maximum number of occurrences.
     * Returns -1 for unbounded (">1").
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
     * Returns the segment IDs in position order.
     */
    public List<String> getSegmentOrder() {
        return new ArrayList<>(segments.keySet());
    }

    /**
     * Returns the segment schema for the given segment ID.
     */
    public Optional<SegmentSchema> getSegmentSchema(String segmentId) {
        SegmentOrGroupSchema schema = segments.get(segmentId);
        if (schema instanceof SegmentSchema segmentSchema) {
            return Optional.of(segmentSchema);
        }
        return Optional.empty();
    }

    /**
     * Returns the nested group schema for the given group ID.
     */
    public Optional<GroupSchema> getNestedGroupSchema(String nestedGroupId) {
        SegmentOrGroupSchema schema = segments.get(nestedGroupId);
        if (schema instanceof GroupSchema groupSchema) {
            return Optional.of(groupSchema);
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupSchema that = (GroupSchema) o;
        return Objects.equals(groupId, that.groupId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(slug, that.slug) &&
                Objects.equals(usage, that.usage) &&
                Objects.equals(count, that.count) &&
                Objects.equals(segments, that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, name, slug, usage, count, segments);
    }

    @Override
    public String toString() {
        return "GroupSchema{" +
                "groupId='" + groupId + '\'' +
                ", name='" + name + '\'' +
                ", usage='" + usage + '\'' +
                ", count='" + count + '\'' +
                ", segmentCount=" + segments.size() +
                '}';
    }
}
