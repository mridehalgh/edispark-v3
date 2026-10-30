package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for segment order preservation in TRADACOMS parsing.
 * 
 * **Feature: tradacoms-parser, Property 2: Segment Order Preservation**
 * **Validates: Requirements 1.2, 2.1**
 */
class SegmentOrderPropertyTest {

    private final EdiParser parser = new EdiParser();

    /**
     * Property 2: Segment Order Preservation
     * 
     * *For any* TRADACOMS input containing N segments in a specific order,
     * the parsed Batch/Message SHALL contain exactly N segments in the same order.
     */
    @Property(tries = 100)
    void parsedMessagePreservesSegmentOrder(
            @ForAll("validMessages") GeneratedMessage generated
    ) {
        // Parse the generated message
        Message message = parser.parseMessage(generated.tradacomsString());
        
        // Get all segments from the parsed message (flattened)
        List<Segment> parsedSegments = message.getAllSegments();
        
        // Verify count matches (excluding MHD and MTR which are header/trailer)
        assertEquals(generated.segmentTags().size(), parsedSegments.size(),
                "Segment count should match");
        
        // Verify order is preserved
        List<String> parsedTags = parsedSegments.stream()
                .map(Segment::tag)
                .collect(Collectors.toList());
        
        assertEquals(generated.segmentTags(), parsedTags,
                "Segment order should be preserved");
    }

    /**
     * Property 2: Segment Order Preservation for Batches
     * 
     * *For any* batch containing multiple messages, the parsed Batch SHALL
     * contain messages in the same order as they appear in the input.
     */
    @Property(tries = 100)
    void parsedBatchPreservesMessageOrder(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        // Parse the generated batch
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        // Verify message count
        assertEquals(generated.messageTypes().size(), batch.messageCount(),
                "Message count should match");
        
        // Verify message order by type
        List<String> parsedTypes = batch.getMessages().stream()
                .map(Message::getMessageType)
                .collect(Collectors.toList());
        
        assertEquals(generated.messageTypes(), parsedTypes,
                "Message order should be preserved");
    }

    /**
     * Property: Segment element values are preserved in order.
     */
    @Property(tries = 100)
    void parsedSegmentPreservesElementOrder(
            @ForAll("segmentsWithElements") GeneratedSegment generated
    ) {
        // Create a message with this segment
        String input = "MHD=1+TEST:9'" + generated.tradacomsString() + "MTR=2'";
        
        Message message = parser.parseMessage(input);
        List<Segment> segments = message.getAllSegments();
        
        assertEquals(1, segments.size(), "Should have one content segment");
        
        Segment parsed = segments.get(0);
        assertEquals(generated.tag(), parsed.tag(), "Tag should match");
        assertEquals(generated.elementValues().size(), parsed.elements().size(),
                "Element count should match");
        
        // Verify element values in order
        for (int i = 0; i < generated.elementValues().size(); i++) {
            assertEquals(generated.elementValues().get(i), parsed.elements().get(i).getValue(),
                    "Element " + i + " value should match");
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<GeneratedMessage> validMessages() {
        return Combinators.combine(
                messageTypes(),
                segmentLists()
        ).as((type, segments) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("MHD=1+").append(type).append(":9'");
            
            List<String> tags = new ArrayList<>();
            for (GeneratedSegment seg : segments) {
                sb.append(seg.tradacomsString());
                tags.add(seg.tag());
            }
            
            sb.append("MTR=").append(segments.size() + 2).append("'");
            
            return new GeneratedMessage(sb.toString(), tags, type);
        });
    }

    @Provide
    Arbitrary<GeneratedBatch> validBatches() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                messageLists()
        ).as((sender, receiver, messages) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("STX=ANAA:1+").append(sender).append("+").append(receiver)
              .append("+260115:120000+REF001'");
            
            List<String> types = new ArrayList<>();
            int msgIndex = 1;
            for (GeneratedMessage msg : messages) {
                // Rebuild message with correct index
                String msgStr = msg.tradacomsString()
                        .replaceFirst("MHD=\\d+", "MHD=" + msgIndex);
                sb.append(msgStr);
                types.add(msg.messageType());
                msgIndex++;
            }
            
            sb.append("END=").append(messages.size()).append("'");
            
            return new GeneratedBatch(sb.toString(), types, sender, receiver);
        });
    }

    @Provide
    Arbitrary<GeneratedSegment> segmentsWithElements() {
        return Combinators.combine(
                segmentTags(),
                elementValueLists()
        ).as((tag, elements) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(tag).append("=");
            sb.append(String.join("+", elements));
            sb.append("'");
            return new GeneratedSegment(sb.toString(), tag, elements);
        });
    }

    private Arbitrary<List<GeneratedSegment>> segmentLists() {
        return segmentsWithElements().list().ofMinSize(1).ofMaxSize(10);
    }

    private Arbitrary<List<GeneratedMessage>> messageLists() {
        return validMessages().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<String> messageTypes() {
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC", "DELIVR", "PRICAT");
    }

    private Arbitrary<String> segmentTags() {
        return Arbitraries.of("CLO", "OLD", "ODD", "CLD", "DNA", "PYT", "CRF", "OIR");
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

    private Arbitrary<List<String>> elementValueLists() {
        return safeElementValues().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<String> safeElementValues() {
        // Generate element values without special characters
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(10);
    }

    // ========== Record Types ==========

    record GeneratedMessage(String tradacomsString, List<String> segmentTags, String messageType) {}
    record GeneratedBatch(String tradacomsString, List<String> messageTypes, String senderId, String receiverId) {}
    record GeneratedSegment(String tradacomsString, String tag, List<String> elementValues) {}
}
