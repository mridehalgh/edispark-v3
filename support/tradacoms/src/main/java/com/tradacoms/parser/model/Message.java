package com.tradacoms.parser.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single TRADACOMS message (business document) within an interchange.
 * Examples: ORDERS, INVOIC, DELIVR, PRICAT, ACKHDR, CREDIT
 */
public final class Message {

    private final String messageType;
    private final int messageIndexInBatch;
    private final String messageControlRef;
    private final List<SegmentOrGroup> content;
    private final Map<String, String> routingKeys;
    private final Segment header;
    private final Segment trailer;

    public Message(
            String messageType,
            int messageIndexInBatch,
            String messageControlRef,
            List<SegmentOrGroup> content,
            Map<String, String> routingKeys,
            Segment header,
            Segment trailer
    ) {
        this.messageType = Objects.requireNonNull(messageType, "messageType must not be null");
        this.messageIndexInBatch = messageIndexInBatch;
        this.messageControlRef = messageControlRef;
        this.content = content != null ? List.copyOf(content) : List.of();
        this.routingKeys = routingKeys != null ? Map.copyOf(routingKeys) : Map.of();
        this.header = header;
        this.trailer = trailer;
    }

    /**
     * Creates a message with minimal required fields.
     */
    public static Message of(String messageType, List<SegmentOrGroup> content) {
        return new Message(messageType, 0, null, content, null, null, null);
    }

    public String getMessageType() {
        return messageType;
    }

    public int getMessageIndexInBatch() {
        return messageIndexInBatch;
    }

    public String getMessageControlRef() {
        return messageControlRef;
    }

    public List<SegmentOrGroup> getContent() {
        return content;
    }

    public Map<String, String> getRoutingKeys() {
        return routingKeys;
    }

    public Segment getHeader() {
        return header;
    }

    public Segment getTrailer() {
        return trailer;
    }

    /**
     * Returns a flattened view of all segments in this message, including those within groups.
     */
    public List<Segment> getAllSegments() {
        List<Segment> segments = new ArrayList<>();
        collectSegments(content, segments);
        return List.copyOf(segments);
    }

    private void collectSegments(List<SegmentOrGroup> items, List<Segment> result) {
        for (SegmentOrGroup item : items) {
            if (item instanceof Segment segment) {
                result.add(segment);
            } else if (item instanceof Group group) {
                collectSegments(group.getContent(), result);
            }
        }
    }

    /**
     * Returns all groups with the specified groupId.
     */
    public List<Group> getGroups(String groupId) {
        Objects.requireNonNull(groupId, "groupId must not be null");
        List<Group> groups = new ArrayList<>();
        collectGroups(content, groupId, groups);
        return List.copyOf(groups);
    }

    private void collectGroups(List<SegmentOrGroup> items, String groupId, List<Group> result) {
        for (SegmentOrGroup item : items) {
            if (item instanceof Group group) {
                if (groupId.equals(group.getGroupId())) {
                    result.add(group);
                }
                // Also search nested subgroups
                collectGroups(group.getContent(), groupId, result);
            }
        }
    }

    /**
     * Returns the first group with the specified groupId.
     */
    public Optional<Group> getFirstGroup(String groupId) {
        List<Group> groups = getGroups(groupId);
        return groups.isEmpty() ? Optional.empty() : Optional.of(groups.get(0));
    }

    /**
     * Returns all top-level groups in this message.
     */
    public List<Group> getAllGroups() {
        List<Group> groups = new ArrayList<>();
        for (SegmentOrGroup item : content) {
            if (item instanceof Group group) {
                groups.add(group);
            }
        }
        return List.copyOf(groups);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return messageIndexInBatch == message.messageIndexInBatch &&
                Objects.equals(messageType, message.messageType) &&
                Objects.equals(messageControlRef, message.messageControlRef) &&
                Objects.equals(content, message.content) &&
                Objects.equals(routingKeys, message.routingKeys) &&
                Objects.equals(header, message.header) &&
                Objects.equals(trailer, message.trailer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageType, messageIndexInBatch, messageControlRef, content, routingKeys, header, trailer);
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageType='" + messageType + '\'' +
                ", messageIndexInBatch=" + messageIndexInBatch +
                ", messageControlRef='" + messageControlRef + '\'' +
                ", contentSize=" + content.size() +
                '}';
    }
}
