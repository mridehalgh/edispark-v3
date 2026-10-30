package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for message filtering correctness in TRADACOMS parsing.
 * 
 * **Feature: tradacoms-parser, Property 3: Message Filtering Correctness**
 * **Validates: Requirements 2.5, 2.6**
 */
class MessageFilteringPropertyTest {

    private final EdiWriter writer = new EdiWriter();

    /**
     * Property 3: Message Filtering Correctness - Allowlist
     * 
     * *For any* batch containing messages of types T1, T2, ..., Tn and *for any*
     * allowlist A, the parsed result SHALL contain exactly those messages where
     * (type ∈ A), preserving relative order of included messages.
     */
    @Property(tries = 100)
    void allowlistFilteringCorrectness(
            @ForAll("batchesWithMixedTypes") Batch original,
            @ForAll("messageTypeSubsets") Set<String> allowlist
    ) {
        // Skip if allowlist is empty (would include all messages)
        Assume.that(!allowlist.isEmpty());
        
        // Serialize the batch
        String serialized = writer.serializeBatch(original);
        
        // Parse with allowlist filter
        ParserConfig config = ParserConfig.builder()
                .messageTypeAllowlist(allowlist)
                .build();
        EdiParser parser = new EdiParser(config);
        Batch filtered = parser.parseBatch(serialized);
        
        // Verify: all messages in result have types in allowlist
        for (Message msg : filtered.getMessages()) {
            assertTrue(allowlist.contains(msg.getMessageType()),
                    "Message type " + msg.getMessageType() + " should be in allowlist " + allowlist);
        }
        
        // Verify: count matches expected
        long expectedCount = original.getMessages().stream()
                .filter(m -> allowlist.contains(m.getMessageType()))
                .count();
        assertEquals(expectedCount, filtered.messageCount(),
                "Filtered message count should match expected");
        
        // Verify: relative order is preserved
        List<String> originalOrder = original.getMessages().stream()
                .filter(m -> allowlist.contains(m.getMessageType()))
                .map(Message::getMessageType)
                .collect(Collectors.toList());
        List<String> filteredOrder = filtered.getMessages().stream()
                .map(Message::getMessageType)
                .collect(Collectors.toList());
        assertEquals(originalOrder, filteredOrder,
                "Relative order of filtered messages should be preserved");
    }

    /**
     * Property 3: Message Filtering Correctness - Denylist
     * 
     * *For any* batch containing messages of types T1, T2, ..., Tn and *for any*
     * denylist D, the parsed result SHALL contain exactly those messages where
     * (type ∉ D), preserving relative order of included messages.
     */
    @Property(tries = 100)
    void denylistFilteringCorrectness(
            @ForAll("batchesWithMixedTypes") Batch original,
            @ForAll("messageTypeSubsets") Set<String> denylist
    ) {
        // Skip if denylist contains all types (would exclude all messages)
        Set<String> allTypes = original.getMessages().stream()
                .map(Message::getMessageType)
                .collect(Collectors.toSet());
        Assume.that(!denylist.containsAll(allTypes));
        
        // Serialize the batch
        String serialized = writer.serializeBatch(original);
        
        // Parse with denylist filter
        ParserConfig config = ParserConfig.builder()
                .messageTypeDenylist(denylist)
                .build();
        EdiParser parser = new EdiParser(config);
        Batch filtered = parser.parseBatch(serialized);
        
        // Verify: no messages in result have types in denylist
        for (Message msg : filtered.getMessages()) {
            assertFalse(denylist.contains(msg.getMessageType()),
                    "Message type " + msg.getMessageType() + " should NOT be in denylist " + denylist);
        }
        
        // Verify: count matches expected
        long expectedCount = original.getMessages().stream()
                .filter(m -> !denylist.contains(m.getMessageType()))
                .count();
        assertEquals(expectedCount, filtered.messageCount(),
                "Filtered message count should match expected");
        
        // Verify: relative order is preserved
        List<String> originalOrder = original.getMessages().stream()
                .filter(m -> !denylist.contains(m.getMessageType()))
                .map(Message::getMessageType)
                .collect(Collectors.toList());
        List<String> filteredOrder = filtered.getMessages().stream()
                .map(Message::getMessageType)
                .collect(Collectors.toList());
        assertEquals(originalOrder, filteredOrder,
                "Relative order of filtered messages should be preserved");
    }

    /**
     * Property: Message indices are sequential after filtering.
     * 
     * *For any* filtered batch, message indices should be 0 to M-1 where M
     * is the number of messages that passed the filter.
     */
    @Property(tries = 100)
    void filteredMessageIndicesAreSequential(
            @ForAll("batchesWithMixedTypes") Batch original,
            @ForAll("messageTypeSubsets") Set<String> allowlist
    ) {
        Assume.that(!allowlist.isEmpty());
        
        String serialized = writer.serializeBatch(original);
        
        ParserConfig config = ParserConfig.builder()
                .messageTypeAllowlist(allowlist)
                .build();
        EdiParser parser = new EdiParser(config);
        Batch filtered = parser.parseBatch(serialized);
        
        // Verify sequential indices
        for (int i = 0; i < filtered.messageCount(); i++) {
            assertEquals(i, filtered.getMessages().get(i).getMessageIndexInBatch(),
                    "Message index should be " + i);
        }
    }

    /**
     * Property: maxMessages limit is respected.
     * 
     * *For any* batch with M messages and maxMessages limit L,
     * the parsed result SHALL contain at most L messages.
     */
    @Property(tries = 100)
    void maxMessagesLimitRespected(
            @ForAll("batchesWithMixedTypes") Batch original,
            @ForAll @IntRange(min = 1, max = 10) int maxMessages
    ) {
        String serialized = writer.serializeBatch(original);
        
        ParserConfig config = ParserConfig.builder()
                .maxMessages(maxMessages)
                .build();
        EdiParser parser = new EdiParser(config);
        Batch filtered = parser.parseBatch(serialized);
        
        // Verify: message count is at most maxMessages
        assertTrue(filtered.messageCount() <= maxMessages,
                "Message count " + filtered.messageCount() + " should be <= " + maxMessages);
        
        // Verify: if original has more messages, we get exactly maxMessages
        if (original.messageCount() > maxMessages) {
            assertEquals(maxMessages, filtered.messageCount(),
                    "Should have exactly maxMessages when original has more");
        } else {
            assertEquals(original.messageCount(), filtered.messageCount(),
                    "Should have all messages when original has fewer than maxMessages");
        }
    }

    /**
     * Property: Combined allowlist and maxMessages filtering.
     * 
     * *For any* batch, allowlist, and maxMessages limit, the result should
     * contain at most maxMessages messages, all of which are in the allowlist.
     */
    @Property(tries = 100)
    void combinedFilteringCorrectness(
            @ForAll("batchesWithMixedTypes") Batch original,
            @ForAll("messageTypeSubsets") Set<String> allowlist,
            @ForAll @IntRange(min = 1, max = 5) int maxMessages
    ) {
        Assume.that(!allowlist.isEmpty());
        
        String serialized = writer.serializeBatch(original);
        
        ParserConfig config = ParserConfig.builder()
                .messageTypeAllowlist(allowlist)
                .maxMessages(maxMessages)
                .build();
        EdiParser parser = new EdiParser(config);
        Batch filtered = parser.parseBatch(serialized);
        
        // Verify: all messages are in allowlist
        for (Message msg : filtered.getMessages()) {
            assertTrue(allowlist.contains(msg.getMessageType()),
                    "Message type should be in allowlist");
        }
        
        // Verify: count is at most maxMessages
        assertTrue(filtered.messageCount() <= maxMessages,
                "Message count should be <= maxMessages");
        
        // Verify: count is at most the number of matching messages in original
        long matchingInOriginal = original.getMessages().stream()
                .filter(m -> allowlist.contains(m.getMessageType()))
                .count();
        assertTrue(filtered.messageCount() <= matchingInOriginal,
                "Message count should be <= matching messages in original");
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<Batch> batchesWithMixedTypes() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                batchIds(),
                mixedTypeMessageLists()
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
    Arbitrary<Set<String>> messageTypeSubsets() {
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC", "DELIVR", "PRICAT")
                .set()
                .ofMinSize(1)
                .ofMaxSize(4);
    }

    private Arbitrary<List<Message>> mixedTypeMessageLists() {
        return messageWithType().list().ofMinSize(2).ofMaxSize(8);
    }

    private Arbitrary<Message> messageWithType() {
        return Combinators.combine(
                messageTypes(),
                validSegmentLists()
        ).as((type, segments) -> {
            List<SegmentOrGroup> content = new ArrayList<>(segments);
            return new Message(type, 0, "1", content, Map.of(), null, null);
        });
    }

    private Arbitrary<List<Segment>> validSegmentLists() {
        return validSegments().list().ofMinSize(1).ofMaxSize(3);
    }

    private Arbitrary<Segment> validSegments() {
        return Combinators.combine(
                segmentTags(),
                validElementLists()
        ).as((tag, elements) -> Segment.of(tag, elements));
    }

    private Arbitrary<List<Element>> validElementLists() {
        return validElements().list().ofMinSize(1).ofMaxSize(3);
    }

    private Arbitrary<Element> validElements() {
        return safeElementValues().map(Element::of);
    }

    private Arbitrary<String> messageTypes() {
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC", "DELIVR", "PRICAT");
    }

    private Arbitrary<String> segmentTags() {
        return Arbitraries.of("CLO", "CRF", "TST", "DAT");
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
                .ofMaxLength(10);
    }
}
