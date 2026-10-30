package com.tradacoms.parser.streaming;

import com.tradacoms.parser.model.Segment;

/**
 * Sealed interface representing events emitted during streaming batch parsing.
 * Events are emitted in order as the parser processes the TRADACOMS input,
 * allowing consumers to process data incrementally without loading the entire
 * batch into memory.
 * 
 * Event sequence for a typical batch:
 * StartBatch -> (StartMessage -> (StartGroup -> SegmentRead* -> EndGroup)* | SegmentRead* -> EndMessage)* -> EndBatch
 */
public sealed interface BatchEvent {

    /**
     * Emitted when the batch envelope (STX segment) is encountered.
     * @param batchId The batch/transmission reference
     * @param stxSegment The raw STX segment
     */
    record StartBatch(String batchId, Segment stxSegment) implements BatchEvent {}

    /**
     * Emitted when a message header (MHD segment) is encountered.
     * @param index The 0-based index of this message within the batch
     * @param type The message type (e.g., "CREDIT", "ORDERS")
     */
    record StartMessage(int index, String type) implements BatchEvent {}

    /**
     * Emitted when a group/loop trigger segment is encountered.
     * @param groupId The group identifier (e.g., "OLD", "CLD")
     * @param loopIndex The 0-based index of this group occurrence
     */
    record StartGroup(String groupId, int loopIndex) implements BatchEvent {}

    /**
     * Emitted for each segment read from the input.
     * @param segment The parsed segment
     */
    record SegmentRead(Segment segment) implements BatchEvent {}

    /**
     * Emitted when a group/loop ends.
     * @param groupId The group identifier
     * @param loopIndex The 0-based index of this group occurrence
     */
    record EndGroup(String groupId, int loopIndex) implements BatchEvent {}

    /**
     * Emitted when a message trailer (MTR segment) is encountered.
     * @param index The 0-based index of this message within the batch
     * @param mtrSegment The raw MTR segment
     */
    record EndMessage(int index, Segment mtrSegment) implements BatchEvent {}

    /**
     * Emitted when the batch trailer (END segment) is encountered.
     * @param endSegment The raw END segment
     */
    record EndBatch(Segment endSegment) implements BatchEvent {}
}
