package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for round-trip consistency in TRADACOMS parsing and writing.
 * 
 * **Feature: tradacoms-parser, Property 1: Round-Trip Consistency**
 * **Validates: Requirements 8.3, 8.4**
 */
class RoundTripPropertyTest {

    private final EdiParser parser = new EdiParser();
    private final EdiWriter writer = new EdiWriter();

    /**
     * Property 1: Round-Trip Consistency for Batches
     * 
     * *For any* valid Batch object, parsing the serialized output and comparing
     * to the original SHALL produce an equivalent Batch (same messages, same
     * segment content, same order).
     */
    @Property(tries = 100)
    void batchRoundTripConsistency(
            @ForAll("validBatches") Batch original
    ) {
        // Serialize the batch
        String serialized = writer.serializeBatch(original);
        
        // Parse it back
        Batch reparsed = parser.parseBatch(serialized);
        
        // Verify equivalence
        assertBatchEquivalent(original, reparsed);
    }

    /**
     * Property 1: Round-Trip Consistency for Messages
     * 
     * *For any* valid Message object, parse(serialize(message)) SHALL equal
     * the original message.
     */
    @Property(tries = 100)
    void messageRoundTripConsistency(
            @ForAll("validMessages") Message original
    ) {
        // Serialize the message
        String serialized = writer.serializeMessage(original);
        
        // Parse it back
        Message reparsed = parser.parseMessage(serialized);
        
        // Verify equivalence
        assertMessageEquivalent(original, reparsed);
    }

    /**
     * Property: Segment content is preserved through round-trip.
     */
    @Property(tries = 100)
    void segmentContentPreservedThroughRoundTrip(
            @ForAll("validSegments") Segment original
    ) {
        Serializer serializer = new Serializer();
        
        // Serialize the segment
        String serialized = serializer.serializeSegment(original);
        
        // Parse it back in a message context
        String messageStr = "MHD=1+TEST:9'" + serialized + "MTR=2'";
        Message message = parser.parseMessage(messageStr);
        
        // Get the content segment (not MHD/MTR)
        List<Segment> segments = message.getAllSegments();
        assertEquals(1, segments.size(), "Should have one content segment");
        
        Segment reparsed = segments.get(0);
        
        // Verify tag
        assertEquals(original.tag(), reparsed.tag(), "Tag should match");
        
        // Verify elements
        assertEquals(original.elements().size(), reparsed.elements().size(),
                "Element count should match");
        
        for (int i = 0; i < original.elements().size(); i++) {
            Element origElem = original.elements().get(i);
            Element reparsedElem = reparsed.elements().get(i);
            
            assertEquals(origElem.components().size(), reparsedElem.components().size(),
                    "Component count should match for element " + i);
            
            for (int j = 0; j < origElem.components().size(); j++) {
                assertEquals(origElem.components().get(j), reparsedElem.components().get(j),
                        "Component " + j + " of element " + i + " should match");
            }
        }
    }

    /**
     * Property: Special characters in element values are preserved through round-trip.
     */
    @Property(tries = 100)
    void specialCharactersPreservedThroughRoundTrip(
            @ForAll("stringsWithSpecialChars") String originalValue
    ) {
        // Create a segment with the value
        Segment original = Segment.of("TST", List.of(Element.of(originalValue)));
        
        Serializer serializer = new Serializer();
        String serialized = serializer.serializeSegment(original);
        
        // Parse it back
        String messageStr = "MHD=1+TEST:9'" + serialized + "MTR=2'";
        Message message = parser.parseMessage(messageStr);
        
        List<Segment> segments = message.getAllSegments();
        assertEquals(1, segments.size());
        
        Segment reparsed = segments.get(0);
        assertEquals(1, reparsed.elements().size());
        assertEquals(originalValue, reparsed.elements().get(0).getValue(),
                "Special characters should be preserved");
    }

    // ========== Assertion Helpers ==========

    private void assertBatchEquivalent(Batch expected, Batch actual) {
        // Verify message count
        assertEquals(expected.messageCount(), actual.messageCount(),
                "Message count should match");
        
        // Verify sender/receiver (may be null in original)
        if (expected.getSenderId() != null) {
            assertEquals(expected.getSenderId(), actual.getSenderId(),
                    "Sender ID should match");
        }
        if (expected.getReceiverId() != null) {
            assertEquals(expected.getReceiverId(), actual.getReceiverId(),
                    "Receiver ID should match");
        }
        
        // Verify each message
        for (int i = 0; i < expected.messageCount(); i++) {
            assertMessageEquivalent(expected.getMessages().get(i), actual.getMessages().get(i));
        }
    }

    private void assertMessageEquivalent(Message expected, Message actual) {
        // Verify message type
        assertEquals(expected.getMessageType(), actual.getMessageType(),
                "Message type should match");
        
        // Get all segments (flattened)
        List<Segment> expectedSegments = expected.getAllSegments();
        List<Segment> actualSegments = actual.getAllSegments();
        
        assertEquals(expectedSegments.size(), actualSegments.size(),
                "Segment count should match");
        
        // Verify each segment
        for (int i = 0; i < expectedSegments.size(); i++) {
            assertSegmentEquivalent(expectedSegments.get(i), actualSegments.get(i), i);
        }
    }

    private void assertSegmentEquivalent(Segment expected, Segment actual, int index) {
        assertEquals(expected.tag(), actual.tag(),
                "Segment " + index + " tag should match");
        
        assertEquals(expected.elements().size(), actual.elements().size(),
                "Segment " + index + " element count should match");
        
        for (int i = 0; i < expected.elements().size(); i++) {
            assertElementEquivalent(expected.elements().get(i), actual.elements().get(i), index, i);
        }
    }

    private void assertElementEquivalent(Element expected, Element actual, int segIndex, int elemIndex) {
        assertEquals(expected.components().size(), actual.components().size(),
                "Segment " + segIndex + " element " + elemIndex + " component count should match");
        
        for (int i = 0; i < expected.components().size(); i++) {
            assertEquals(expected.components().get(i), actual.components().get(i),
                    "Segment " + segIndex + " element " + elemIndex + " component " + i + " should match");
        }
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
            // Reassign message indices
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

    @Provide
    Arbitrary<Segment> validSegments() {
        return Combinators.combine(
                segmentTags(),
                validElementLists()
        ).as((tag, elements) -> Segment.of(tag, elements));
    }

    private Arbitrary<List<Message>> validMessageLists() {
        return validMessages().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<List<Segment>> validSegmentLists() {
        return validSegments().list().ofMinSize(1).ofMaxSize(10);
    }

    private Arbitrary<List<Element>> validElementLists() {
        return validElements().list().ofMinSize(1).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Element> validElements() {
        return Arbitraries.oneOf(
                // Simple element
                safeElementValues().map(Element::of),
                // Composite element
                safeElementValues().list().ofMinSize(2).ofMaxSize(4).map(Element::of)
        );
    }

    @Provide
    Arbitrary<String> stringsWithSpecialChars() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 '+:=?")
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    private Arbitrary<String> messageTypes() {
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC", "DELIVR", "PRICAT");
    }

    private Arbitrary<String> segmentTags() {
        return Arbitraries.of("CLO", "OLD", "ODD", "CLD", "DNA", "PYT", "CRF", "OIR", "TST");
    }

    private Arbitrary<String> senderIds() {
        return Arbitraries.strings()
                .numeric()
                .ofLength(13);
    }

    private Arbitrary<String> receiverIds() {
        return Arbitraries.strings()
                .numeric()
                .ofLength(13);
    }

    private Arbitrary<String> batchIds() {
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
