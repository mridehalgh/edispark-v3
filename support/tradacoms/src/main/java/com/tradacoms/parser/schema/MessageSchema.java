package com.tradacoms.parser.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Schema for a complete message type, loaded from JSON schema files.
 * Aligns with existing TRADACOMS JSON schema format (e.g., CREDIT.json).
 * 
 * JSON Schema Structure:
 * - messages[].id: Message type identifier (e.g., "CREDIT")
 * - messages[].messageClass: Message classification
 * - messages[].segments: Map of segment/group definitions
 *   - Segments have "type": "SEGMENT"
 *   - Groups have "groupId" and nested "segments"
 */
public final class MessageSchema {

    private final String id;
    private final String messageClass;
    private final String name;
    private final Map<String, SegmentOrGroupSchema> segments;

    public MessageSchema(
            String id,
            String messageClass,
            String name,
            Map<String, SegmentOrGroupSchema> segments
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.messageClass = messageClass;
        this.name = name;
        this.segments = segments != null ? new LinkedHashMap<>(segments) : new LinkedHashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getMessageClass() {
        return messageClass;
    }

    public String getName() {
        return name;
    }

    public Map<String, SegmentOrGroupSchema> getSegments() {
        return Map.copyOf(segments);
    }

    /**
     * Returns the group schema for the given group ID.
     */
    public Optional<GroupSchema> getGroupSchema(String groupId) {
        SegmentOrGroupSchema schema = segments.get(groupId);
        if (schema instanceof GroupSchema groupSchema) {
            return Optional.of(groupSchema);
        }
        // Also search nested groups
        for (SegmentOrGroupSchema s : segments.values()) {
            if (s instanceof GroupSchema gs) {
                Optional<GroupSchema> nested = findNestedGroup(gs, groupId);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<GroupSchema> findNestedGroup(GroupSchema parent, String groupId) {
        for (SegmentOrGroupSchema s : parent.getSegments().values()) {
            if (s instanceof GroupSchema gs) {
                if (groupId.equals(gs.getGroupId())) {
                    return Optional.of(gs);
                }
                Optional<GroupSchema> nested = findNestedGroup(gs, groupId);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the segment schema for the given segment ID.
     */
    public Optional<SegmentSchema> getSegmentSchema(String segmentId) {
        SegmentOrGroupSchema schema = segments.get(segmentId);
        if (schema instanceof SegmentSchema segmentSchema) {
            return Optional.of(segmentSchema);
        }
        // Also search within groups
        for (SegmentOrGroupSchema s : segments.values()) {
            if (s instanceof GroupSchema gs) {
                Optional<SegmentSchema> found = findSegmentInGroup(gs, segmentId);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<SegmentSchema> findSegmentInGroup(GroupSchema group, String segmentId) {
        for (SegmentOrGroupSchema s : group.getSegments().values()) {
            if (s instanceof SegmentSchema ss && segmentId.equals(ss.getId())) {
                return Optional.of(ss);
            }
            if (s instanceof GroupSchema gs) {
                Optional<SegmentSchema> found = findSegmentInGroup(gs, segmentId);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns true if the given segment tag is a group trigger.
     * A segment is a group trigger if it's the first segment in a group definition.
     */
    public boolean isGroupTrigger(String segmentTag) {
        for (SegmentOrGroupSchema s : segments.values()) {
            if (s instanceof GroupSchema gs) {
                if (isGroupTriggerInGroup(gs, segmentTag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isGroupTriggerInGroup(GroupSchema group, String segmentTag) {
        // The first segment in a group is the trigger
        List<String> order = group.getSegmentOrder();
        if (!order.isEmpty()) {
            String firstKey = order.get(0);
            SegmentOrGroupSchema first = group.getSegments().get(firstKey);
            if (first instanceof SegmentSchema ss && segmentTag.equals(ss.getId())) {
                return true;
            }
        }
        // Check nested groups
        for (SegmentOrGroupSchema s : group.getSegments().values()) {
            if (s instanceof GroupSchema gs) {
                if (isGroupTriggerInGroup(gs, segmentTag)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the segment IDs in position order.
     */
    public List<String> getSegmentOrder() {
        return new ArrayList<>(segments.keySet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageSchema that = (MessageSchema) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(messageClass, that.messageClass) &&
                Objects.equals(name, that.name) &&
                Objects.equals(segments, that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, messageClass, name, segments);
    }

    @Override
    public String toString() {
        return "MessageSchema{" +
                "id='" + id + '\'' +
                ", messageClass='" + messageClass + '\'' +
                ", name='" + name + '\'' +
                ", segmentCount=" + segments.size() +
                '}';
    }
}
