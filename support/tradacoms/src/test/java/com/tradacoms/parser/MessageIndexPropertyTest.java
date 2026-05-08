package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for message index assignment in TRADACOMS batch parsing.
 * 
 * **Feature: tradacoms-parser, Property 12: Message Index Assignment**
 * **Validates: Requirements 2.3**
 */
class MessageIndexPropertyTest {

    private final EdiParser parser = new EdiParser();

    /**
     * Property 12: Message Index Assignment
     * 
     * *For any* batch containing M messages, each message SHALL have a unique
     * messageIndexInBatch value from 0 to M-1, assigned in parse order.
     */
    @Property(tries = 100)
    void messageIndicesAreSequentialFromZero(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        // Parse the generated batch
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        int messageCount = batch.messageCount();
        
        // Verify we have the expected number of messages
        assertEquals(generated.messageCount(), messageCount,
                "Message count should match generated count");
        
        // Verify indices are 0 to M-1
        List<Integer> indices = batch.getMessages().stream()
                .map(Message::getMessageIndexInBatch)
                .toList();
        
        List<Integer> expected = IntStream.range(0, messageCount)
                .boxed()
                .toList();
        
        assertEquals(expected, indices,
                "Message indices should be 0 to " + (messageCount - 1) + " in order");
    }

    /**
     * Property: All message indices are unique within a batch.
     */
    @Property(tries = 100)
    void messageIndicesAreUnique(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        Set<Integer> uniqueIndices = new HashSet<>();
        for (Message message : batch.getMessages()) {
            boolean added = uniqueIndices.add(message.getMessageIndexInBatch());
            assertTrue(added, "Message index " + message.getMessageIndexInBatch() + 
                    " should be unique");
        }
        
        assertEquals(batch.messageCount(), uniqueIndices.size(),
                "All indices should be unique");
    }

    /**
     * Property: Message indices start at 0.
     */
    @Property(tries = 100)
    void firstMessageIndexIsZero(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        if (batch.messageCount() > 0) {
            assertEquals(0, batch.getMessages().get(0).getMessageIndexInBatch(),
                    "First message index should be 0");
        }
    }

    /**
     * Property: Last message index is M-1 where M is message count.
     */
    @Property(tries = 100)
    void lastMessageIndexIsCountMinusOne(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        int messageCount = batch.messageCount();
        if (messageCount > 0) {
            int lastIndex = batch.getMessages().get(messageCount - 1).getMessageIndexInBatch();
            assertEquals(messageCount - 1, lastIndex,
                    "Last message index should be " + (messageCount - 1));
        }
    }

    /**
     * Property: Message indices are assigned in parse order, not by message type.
     */
    @Property(tries = 100)
    void indicesAssignedInParseOrderNotByType(
            @ForAll("batchesWithMixedTypes") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        // Verify that indices follow parse order regardless of type
        for (int i = 0; i < batch.messageCount(); i++) {
            Message message = batch.getMessages().get(i);
            assertEquals(i, message.getMessageIndexInBatch(),
                    "Message at position " + i + " should have index " + i + 
                    " regardless of type " + message.getMessageType());
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<GeneratedBatch> validBatches() {
        return Arbitraries.integers().between(1, 10)
                .flatMap(count -> generateBatch(count, false));
    }

    @Provide
    Arbitrary<GeneratedBatch> batchesWithMixedTypes() {
        return Arbitraries.integers().between(2, 8)
                .flatMap(count -> generateBatch(count, true));
    }

    private Arbitrary<GeneratedBatch> generateBatch(int messageCount, boolean mixTypes) {
        Arbitrary<List<String>> typesArb;
        if (mixTypes) {
            typesArb = messageTypes().list().ofSize(messageCount);
        } else {
            typesArb = messageTypes().map(t -> 
                    Collections.nCopies(messageCount, t));
        }
        
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                typesArb
        ).as((sender, receiver, types) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("STX=ANAA:1+").append(sender).append("+").append(receiver)
              .append("+260115:120000+REF001'");
            
            for (int i = 0; i < types.size(); i++) {
                String type = types.get(i);
                sb.append("MHD=").append(i + 1).append("+").append(type).append(":9'");
                sb.append("CLO=").append(100000 + i).append("'");
                sb.append("MTR=2'");
            }
            
            sb.append("END=").append(types.size()).append("'");
            
            return new GeneratedBatch(sb.toString(), types.size(), types, sender, receiver);
        });
    }

    private Arbitrary<String> messageTypes() {
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC", "DELIVR", "PRICAT", "ACKHDR");
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

    // ========== Record Types ==========

    record GeneratedBatch(
            String tradacomsString, 
            int messageCount, 
            List<String> messageTypes,
            String senderId, 
            String receiverId
    ) {}
}
