package com.tradacoms.parser.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a repeating loop structure in TRADACOMS.
 * Groups contain segments and may nest other groups (subgroups).
 * Examples: OLD (Order Line Detail) loop, DNA (Delivery Note Address) loop, CLD (Credit Line Detail) loop
 */
public final class Group implements SegmentOrGroup {

    private final String groupId;
    private final int loopIndex;
    private final int maxOccurs;
    private final List<SegmentOrGroup> content;

    public Group(String groupId, int loopIndex, int maxOccurs, List<SegmentOrGroup> content) {
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.loopIndex = loopIndex;
        this.maxOccurs = maxOccurs;
        this.content = content != null ? List.copyOf(content) : List.of();
    }

    /**
     * Creates a group with default maxOccurs of -1 (unbounded).
     */
    public static Group of(String groupId, int loopIndex, List<SegmentOrGroup> content) {
        return new Group(groupId, loopIndex, -1, content);
    }

    public String getGroupId() {
        return groupId;
    }

    public int getLoopIndex() {
        return loopIndex;
    }

    public int getMaxOccurs() {
        return maxOccurs;
    }

    public List<SegmentOrGroup> getContent() {
        return content;
    }

    /**
     * Returns all segments in this group (not including nested subgroups' segments).
     */
    public List<Segment> getSegments() {
        List<Segment> segments = new ArrayList<>();
        for (SegmentOrGroup item : content) {
            if (item instanceof Segment segment) {
                segments.add(segment);
            }
        }
        return List.copyOf(segments);
    }

    /**
     * Returns all segments with the specified tag.
     */
    public List<Segment> getSegments(String tag) {
        Objects.requireNonNull(tag, "tag must not be null");
        List<Segment> segments = new ArrayList<>();
        for (SegmentOrGroup item : content) {
            if (item instanceof Segment segment && tag.equals(segment.tag())) {
                segments.add(segment);
            }
        }
        return List.copyOf(segments);
    }

    /**
     * Returns the first segment with the specified tag.
     */
    public Optional<Segment> getFirstSegment(String tag) {
        Objects.requireNonNull(tag, "tag must not be null");
        for (SegmentOrGroup item : content) {
            if (item instanceof Segment segment && tag.equals(segment.tag())) {
                return Optional.of(segment);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns all subgroups with the specified groupId.
     */
    public List<Group> getSubgroups(String subgroupId) {
        Objects.requireNonNull(subgroupId, "subgroupId must not be null");
        List<Group> subgroups = new ArrayList<>();
        for (SegmentOrGroup item : content) {
            if (item instanceof Group group && subgroupId.equals(group.getGroupId())) {
                subgroups.add(group);
            }
        }
        return List.copyOf(subgroups);
    }

    /**
     * Returns all subgroups in this group.
     */
    public List<Group> getAllSubgroups() {
        List<Group> subgroups = new ArrayList<>();
        for (SegmentOrGroup item : content) {
            if (item instanceof Group group) {
                subgroups.add(group);
            }
        }
        return List.copyOf(subgroups);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return loopIndex == group.loopIndex &&
                maxOccurs == group.maxOccurs &&
                Objects.equals(groupId, group.groupId) &&
                Objects.equals(content, group.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, loopIndex, maxOccurs, content);
    }

    @Override
    public String toString() {
        return "Group{" +
                "groupId='" + groupId + '\'' +
                ", loopIndex=" + loopIndex +
                ", maxOccurs=" + maxOccurs +
                ", contentSize=" + content.size() +
                '}';
    }
}
