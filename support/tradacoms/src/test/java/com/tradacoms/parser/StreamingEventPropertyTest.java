package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import com.tradacoms.parser.streaming.BatchEvent;
import com.tradacoms.parser.streaming.BatchEventReader;
import net.jqwik.api.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for streaming event accuracy in TRADACOMS parsing.
 * 
 * **Feature: tradacoms-parser, Property 4: Streaming Event Accuracy**
 * **Validates: Requirements 3.1, 3.2, 3.3**
 */
class StreamingEventPropertyTest {

    private final EdiParser parser = new EdiParser();
    private final EdiWriter writer = new EdiWriter();

    /**
     * Property 4: Streaming Event Accuracy - Message Count
     * 
     * *For any* batch containing M messages, streaming SHALL emit exactly M pairs
     * of START_MESSAGE/END_MESSAGE events.
     */
    @Property(tries = 100)
    void streamingEmitsCorrectMessageEventCount(
            @ForAll("validBatches") Batch batch
    ) {
        // Serialize the batch
        String serialized = writer.serializeBatch(batch);
        
        // Stream and count message events
        int startMessageCount = 0;
        int endMessageCount = 0;
        
        try (BatchEventReader reader = new BatchEventReader(
                new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)),
                ParserConfig.defaults(),
                Map.of())) {
            
            while (reader.hasNext()) {
                BatchEvent event = reader.next();
                if (event instanceof BatchEvent.StartMessage) {
                    startMessageCount++;
                } else if (event instanceof BatchEvent.EndMessage) {
                    endMessageCount++;
                }
            }
        }
        
        assertEquals(batch.messageCount(), startMessageCount,
                "Should emit exactly M StartMessage events for M messages");
        assertEquals(batch.messageCount(), endMessageCount,
                "Should emit exactly M EndMessage events for M messages");
        assertEquals(startMessageCount, endMessageCount,
                "StartMessage and EndMessage counts should match");
    }

    /**
     * Property 4: Streaming Event Accuracy - Segment Events Match DOM Parse
     * 
     * *For any* batch, the segment events between each START_MESSAGE/END_MESSAGE pair
     * SHALL match the segments in the corresponding Message when parsed via DOM.
     */
    @Property(tries = 100)
    void streamingSegmentEventsMatchDomParse(
            @ForAll("validBatches") Batch batch
    ) {
        // Serialize the batch
        String serialized = writer.serializeBatch(batch);
        
        // Parse via DOM
        Batch domParsed = parser.parseBatch(serialized);
        
        // Stream and collect segments per message
        List<List<Segment>> streamedSegmentsPerMessage = new ArrayList<>();
        List<Segment> currentMessageSegments = null;
        
        try (BatchEventReader reader = new BatchEventReader(
                new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)),
                ParserConfig.defaults(),
                Map.of())) {
            
            while (reader.hasNext()) {
                BatchEvent event = reader.next();
                
                if (event instanceof BatchEvent.StartMessage) {
                    currentMessageSegments = new ArrayList<>();
                } else if (event instanceof BatchEvent.SegmentRead segmentRead) {
                    if (currentMessageSegments != null) {
                        // Skip MHD and MTR as they're not in content
                        String tag = segmentRead.segment().tag();
                        if (!"MHD".equals(tag) && !"MTR".equals(tag)) {
                            currentMessageSegments.add(segmentRead.segment());
                        }
                    }
                } else if (event instanceof BatchEvent.EndMessage) {
                    if (currentMessageSegments != null) {
                        streamedSegmentsPerMessage.add(currentMessageSegments);
                        currentMessageSegments = null;
                    }
                }
            }
        }
        
        // Verify segment counts match
        assertEquals(domParsed.messageCount(), streamedSegmentsPerMessage.size(),
                "Should have same number of messages");
        
        for (int i = 0; i < domParsed.messageCount(); i++) {
            Message domMessage = domParsed.getMessages().get(i);
            List<Segment> domSegments = domMessage.getAllSegments();
            List<Segment> streamedSegments = streamedSegmentsPerMessage.get(i);
            
            assertEquals(domSegments.size(), streamedSegments.size(),
                    "Message " + i + " should have same segment count");
            
            // Verify segment tags match in order
            for (int j = 0; j < domSegments.size(); j++) {
                assertEquals(domSegments.get(j).tag(), streamedSegments.get(j).tag(),
                        "Message " + i + " segment " + j + " tag should match");
            }
        }
    }

    /**
     * Property 4: Streaming Event Accuracy - Message Index Assignment
     * 
     * *For any* batch, the message indices in START_MESSAGE events SHALL be
     * sequential starting from 0.
     */
    @Property(tries = 100)
    void streamingMessageIndicesAreSequential(
            @ForAll("validBatches") Batch batch
    ) {
        // Serialize the batch
        String serialized = writer.serializeBatch(batch);
        
        // Stream and collect message indices
        List<Integer> messageIndices = new ArrayList<>();
        
        try (BatchEventReader reader = new BatchEventReader(
                new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)),
                ParserConfig.defaults(),
                Map.of())) {
            
            while (reader.hasNext()) {
                BatchEvent event = reader.next();
                if (event instanceof BatchEvent.StartMessage startMsg) {
                    messageIndices.add(startMsg.index());
                }
            }
        }
        
        // Verify sequential indices starting from 0
        for (int i = 0; i < messageIndices.size(); i++) {
            assertEquals(i, messageIndices.get(i),
                    "Message index " + i + " should be " + i);
        }
    }

    /**
     * Property 4: Streaming Event Accuracy - Batch Envelope Events
     * 
     * *For any* batch, streaming SHALL emit exactly one StartBatch and one EndBatch event.
     */
    @Property(tries = 100)
    void streamingEmitsExactlyOneBatchEnvelopeEvents(
            @ForAll("validBatches") Batch batch
    ) {
        // Serialize the batch
        String serialized = writer.serializeBatch(batch);
        
        // Stream and count batch events
        int startBatchCount = 0;
        int endBatchCount = 0;
        
        try (BatchEventReader reader = new BatchEventReader(
                new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)),
                ParserConfig.defaults(),
                Map.of())) {
            
            while (reader.hasNext()) {
                BatchEvent event = reader.next();
                if (event instanceof BatchEvent.StartBatch) {
                    startBatchCount++;
                } else if (event instanceof BatchEvent.EndBatch) {
                    endBatchCount++;
                }
            }
        }
        
        assertEquals(1, startBatchCount, "Should emit exactly one StartBatch event");
        assertEquals(1, endBatchCount, "Should emit exactly one EndBatch event");
    }

    /**
     * Property: streamMessages() produces same messages as DOM parsing.
     * 
     * *For any* batch, streaming messages via streamMessages() SHALL produce
     * messages equivalent to those from DOM parsing.
     */
    @Property(tries = 100)
    void streamMessagesMatchesDomParsing(
            @ForAll("validBatches") Batch batch
    ) {
        // Serialize the batch
        String serialized = writer.serializeBatch(batch);
        
        // Parse via DOM
        Batch domParsed = parser.parseBatch(serialized);
        
        // Stream messages
        List<Message> streamedMessages = new ArrayList<>();
        Iterator<Message> messageIterator = parser.streamMessages(
                new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)));
        
        while (messageIterator.hasNext()) {
            streamedMessages.add(messageIterator.next());
        }
        
        // Verify message count
        assertEquals(domParsed.messageCount(), streamedMessages.size(),
                "Should have same number of messages");
        
        // Verify each message
        for (int i = 0; i < domParsed.messageCount(); i++) {
            Message domMessage = domParsed.getMessages().get(i);
            Message streamedMessage = streamedMessages.get(i);
            
            assertEquals(domMessage.getMessageType(), streamedMessage.getMessageType(),
                    "Message " + i + " type should match");
            assertEquals(domMessage.getMessageIndexInBatch(), streamedMessage.getMessageIndexInBatch(),
                    "Message " + i + " index should match");
            
            // Verify segment count
            List<Segment> domSegments = domMessage.getAllSegments();
            List<Segment> streamedSegments = streamedMessage.getAllSegments();
            
            assertEquals(domSegments.size(), streamedSegments.size(),
                    "Message " + i + " should have same segment count");
        }
    }

    /**
     * Property: Group events are properly paired.
     * 
     * *For any* batch with groups, every StartGroup event SHALL have a matching
     * EndGroup event with the same groupId and loopIndex.
     */
    @Property(tries = 100)
    void groupEventsAreProperlyPaired(
            @ForAll("batchesWithGroups") Batch batch
    ) {
        // Serialize the batch
        String serialized = writer.serializeBatch(batch);
        
        // Stream and track group events
        Deque<BatchEvent.StartGroup> groupStack = new ArrayDeque<>();
        List<String> errors = new ArrayList<>();
        
        try (BatchEventReader reader = new BatchEventReader(
                new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8)),
                ParserConfig.defaults(),
                Map.of())) {
            
            while (reader.hasNext()) {
                BatchEvent event = reader.next();
                
                if (event instanceof BatchEvent.StartGroup startGroup) {
                    groupStack.push(startGroup);
                } else if (event instanceof BatchEvent.EndGroup endGroup) {
                    if (groupStack.isEmpty()) {
                        errors.add("EndGroup without matching StartGroup: " + endGroup.groupId());
                    } else {
                        BatchEvent.StartGroup startGroup = groupStack.pop();
                        if (!startGroup.groupId().equals(endGroup.groupId())) {
                            errors.add("Mismatched group IDs: start=" + startGroup.groupId() + 
                                    ", end=" + endGroup.groupId());
                        }
                        if (startGroup.loopIndex() != endGroup.loopIndex()) {
                            errors.add("Mismatched loop indices: start=" + startGroup.loopIndex() + 
                                    ", end=" + endGroup.loopIndex());
                        }
                    }
                }
            }
        }
        
        // Check for unclosed groups
        while (!groupStack.isEmpty()) {
            BatchEvent.StartGroup unclosed = groupStack.pop();
            errors.add("Unclosed group: " + unclosed.groupId() + "[" + unclosed.loopIndex() + "]");
        }
        
        assertTrue(errors.isEmpty(), 
                "Group events should be properly paired. Errors: " + String.join(", ", errors));
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
    Arbitrary<Batch> batchesWithGroups() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                batchIds(),
                messagesWithGroups().list().ofMinSize(1).ofMaxSize(3)
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

    private Arbitrary<Message> messagesWithGroups() {
        return Combinators.combine(
                messageTypes(),
                groupLists()
        ).as((type, groups) -> {
            List<SegmentOrGroup> content = new ArrayList<>();
            // Add a non-group segment first
            content.add(Segment.of("CLO", List.of(Element.of("123456"))));
            // Add groups
            content.addAll(groups);
            return new Message(type, 0, "1", content, Map.of(), null, null);
        });
    }

    private Arbitrary<List<com.tradacoms.parser.model.Group>> groupLists() {
        return validGroups().list().ofMinSize(1).ofMaxSize(3);
    }

    private Arbitrary<com.tradacoms.parser.model.Group> validGroups() {
        return Combinators.combine(
                groupIds(),
                Arbitraries.integers().between(0, 5),
                groupSegmentLists()
        ).as((groupId, loopIndex, segments) -> {
            List<SegmentOrGroup> content = new ArrayList<>();
            // Add trigger segment
            content.add(Segment.groupTrigger(groupId, List.of(Element.of("1"))));
            // Add other segments
            content.addAll(segments);
            return com.tradacoms.parser.model.Group.of(groupId, loopIndex, content);
        });
    }

    private Arbitrary<List<Segment>> groupSegmentLists() {
        return groupSegments().list().ofMinSize(0).ofMaxSize(3);
    }

    private Arbitrary<Segment> groupSegments() {
        return Combinators.combine(
                Arbitraries.of("ODD", "DNB", "FTX"),
                validElementLists()
        ).as(Segment::of);
    }

    private Arbitrary<List<Message>> validMessageLists() {
        return validMessages().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<Message> validMessages() {
        return Combinators.combine(
                messageTypes(),
                validSegmentLists()
        ).as((type, segments) -> {
            List<SegmentOrGroup> content = new ArrayList<>(segments);
            return new Message(type, 0, "1", content, Map.of(), null, null);
        });
    }

    private Arbitrary<List<Segment>> validSegmentLists() {
        return validSegments().list().ofMinSize(1).ofMaxSize(10);
    }

    private Arbitrary<Segment> validSegments() {
        return Combinators.combine(
                segmentTags(),
                validElementLists()
        ).as(Segment::of);
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
        return Arbitraries.of("CLO", "CRF", "OIR", "TST", "FTX", "DNB");
    }

    private Arbitrary<String> groupIds() {
        return Arbitraries.of("OLD", "CLD", "ILD", "DLD");
    }

    private Arbitrary<String> senderIds() {
        return Arbitraries.strings().numeric().ofLength(13);
    }

    private Arbitrary<String> receiverIds() {
        return Arbitraries.strings().numeric().ofLength(13);
    }

    private Arbitrary<String> batchIds() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
    }

    private Arbitrary<String> safeElementValues() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(15);
    }
}
