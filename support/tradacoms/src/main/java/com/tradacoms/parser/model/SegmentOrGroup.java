package com.tradacoms.parser.model;

/**
 * Sealed interface for message content - either a Segment or a Group.
 * Enables type-safe traversal of hierarchical message structure.
 */
public sealed interface SegmentOrGroup permits Segment, Group {
}
