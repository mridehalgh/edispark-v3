package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for control total computation in TRADACOMS writing.
 * 
 * **Feature: tradacoms-parser, Property 8: Control Total Computation**
 * **Validates: Requirements 6.2, 6.3**
 */
class ControlTotalPropertyTest {

    private final EdiParser parser = new EdiParser();
    private final EdiWriter writer = new EdiWriter();

    /**
     * Property 8: Control Total Computation - Message Count
     * 
     * *For any* batch written with computeControlTotals=true, the END segment's
     * message count SHALL equal the actual number of MHD/MTR pairs.
     */
    @Property(tries = 100)
    void endSegmentMessageCountMatchesActualMessages(
            @ForAll("validBatches") Batch original
    ) {
        // Serialize the batch
        String serialized = writer.serializeBatch(original);
        
        // Parse it back to get the END segment
        Batch reparsed = parser.parseBatch(serialized);
        
        // Get the END segment
        Segment endSegment = reparsed.getRawTrailer();
        assertNotNull(endSegment, "END segment should be present");
        assertEquals(TradacomsSyntax.END, endSegment.tag(), "Should be END segment");
        
        // Verify message count in END segment matches actual messages
        int actualMessageCount = reparsed.messageCount();
        String endMessageCount = endSegment.getElementValue(0);
        
        assertEquals(String.valueOf(actualMessageCount), endMessageCount,
                "END segment message count should match actual message count");
    }

    /**
     * Property 8: Control Total Computation - MTR Segment Count
     * 
     * *For any* message, the MTR segment's segment count SHALL match the actual
     * number of segments in the message (including MHD and MTR).
     */
    @Property(tries = 100)
    void mtrSegmentCountMatchesActualSegments(
            @ForAll("validMessages") Message original
    ) {
        // Serialize the message
        String serialized = writer.serializeMessage(original);
        
        // Parse it back
        Message reparsed = parser.parseMessage(serialized);
        
        // Get the MTR segment
        Segment mtrSegment = reparsed.getTrailer();
        assertNotNull(mtrSegment, "MTR segment should be present");
        assertEquals(TradacomsSyntax.MTR, mtrSegment.tag(), "Should be MTR segment");
        
        // Count actual segments: MHD + content segments + MTR
        int contentSegmentCount = countSegments(reparsed.getContent());
        int totalSegmentCount = 1 + contentSegmentCount + 1; // MHD + content + MTR
        
        String mtrCount = mtrSegment.getElementValue(0);
        assertEquals(String.valueOf(totalSegmentCount), mtrCount,
                "MTR segment count should match actual segment count");
    }

    /**
     * Property: Message count in END segment is consistent across round-trips.
     */
    @Property(tries = 100)
    void messageCountConsistentAcrossRoundTrips(
            @ForAll("validBatches") Batch original
    ) {
        // First round-trip
        String serialized1 = writer.serializeBatch(original);
        Batch reparsed1 = parser.parseBatch(serialized1);
        
        // Second round-trip
        String serialized2 = writer.serializeBatch(reparsed1);
        Batch reparsed2 = parser.parseBatch(serialized2);
        
        // Message counts should be consistent
        assertEquals(reparsed1.messageCount(), reparsed2.messageCount(),
                "Message count should be consistent across round-trips");
        
        // END segment counts should match
        String endCount1 = reparsed1.getRawTrailer().getElementValue(0);
        String endCount2 = reparsed2.getRawTrailer().getElementValue(0);
        assertEquals(endCount1, endCount2,
                "END segment message count should be consistent across round-trips");
    }

    /**
     * Property: Segment counts in MTR are consistent across round-trips.
     */
    @Property(tries = 100)
    void segmentCountConsistentAcrossRoundTrips(
            @ForAll("validMessages") Message original
    ) {
        // First round-trip
        String serialized1 = writer.serializeMessage(original);
        Message reparsed1 = parser.parseMessage(serialized1);
        
        // Second round-trip
        String serialized2 = writer.serializeMessage(reparsed1);
        Message reparsed2 = parser.parseMessage(serialized2);
        
        // MTR segment counts should match
        String mtrCount1 = reparsed1.getTrailer().getElementValue(0);
        String mtrCount2 = reparsed2.getTrailer().getElementValue(0);
        assertEquals(mtrCount1, mtrCount2,
                "MTR segment count should be consistent across round-trips");
    }

    /**
     * Property: Empty batch has message count of 0.
     */
    @Property(tries = 10)
    void emptyBatchHasZeroMessageCount(
            @ForAll("senderIds") String senderId,
            @ForAll("receiverIds") String receiverId
    ) {
        // Create an empty batch
        Batch emptyBatch = new Batch(
                "REF001",
                senderId,
                receiverId,
                Instant.now(),
                List.of(),
                null,
                null,
                null
        );
        
        // Serialize and parse
        String serialized = writer.serializeBatch(emptyBatch);
        Batch reparsed = parser.parseBatch(serialized);
        
        // Verify END segment has count 0
        assertEquals(0, reparsed.messageCount());
        assertEquals("0", reparsed.getRawTrailer().getElementValue(0),
                "Empty batch should have message count 0 in END segment");
    }

    // ========== Helper Methods ==========

    private int countSegments(List<SegmentOrGroup> content) {
        int count = 0;
        for (SegmentOrGroup item : content) {
            if (item instanceof Segment) {
                count++;
            } else if (item instanceof com.tradacoms.parser.model.Group group) {
                count += countSegments(group.getContent());
            }
        }
        return count;
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<Batch> validBatches() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                batchIds(),
                validMessageLists()
        ).as((sender, receiver, batchId, messages) -> {
            List<Message> indexedMessages = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                indexedMessages.add(new Message(
                        m.getMessageType(),
                        i,
                        String.valueOf(i + 1),
                        m.getContent(),
                        m.getRoutingKeys(),
                        m.getHeader(),
                        m.getTrailer()
                ));
            }
            
            return new Batch(
                    batchId,
                    sender,
                    receiver,
                    Instant.now(),
                    indexedMessages,
                    null,
                    null,
                    null
            );
        });
    }

    @Provide
    Arbitrary<Message> validMessages() {
        return Combinators.combine(
                messageTypes(),
                validSegmentLists()
        ).as((type, segments) -> {
            List<SegmentOrGroup> content = new ArrayList<>(segments);
            return new Message(type, 0, "1", content, Map.of(), null, null);
        });
    }

    private Arbitrary<List<Message>> validMessageLists() {
        return validMessages().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<List<Segment>> validSegmentLists() {
        return validSegments().list().ofMinSize(1).ofMaxSize(10);
    }

    private Arbitrary<Segment> validSegments() {
        return Combinators.combine(
                segmentTags(),
                validElementLists()
        ).as((tag, elements) -> Segment.of(tag, elements));
    }

    private Arbitrary<List<Element>> validElementLists() {
        return validElements().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<Element> validElements() {
        return Arbitraries.oneOf(
                safeElementValues().map(Element::of),
                safeElementValues().list().ofMinSize(2).ofMaxSize(4).map(Element::of)
        );
    }

    private Arbitrary<String> messageTypes() {
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC", "DELIVR", "PRICAT");
    }

    private Arbitrary<String> segmentTags() {
        return Arbitraries.of("CLO", "OLD", "ODD", "CLD", "DNA", "PYT", "CRF", "OIR");
    }

    @Provide
    Arbitrary<String> senderIds() {
        return Arbitraries.strings()
                .numeric()
                .ofLength(13);
    }

    @Provide
    Arbitrary<String> receiverIds() {
        return Arbitraries.strings()
                .numeric()
                .ofLength(13);
    }

    @Provide
    Arbitrary<String> batchIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(10);
    }

    private Arbitrary<String> safeElementValues() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(15);
    }
}
