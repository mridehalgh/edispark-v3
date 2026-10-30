package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for split output correctness in TRADACOMS batch splitting.
 * 
 * **Feature: tradacoms-parser, Property 7: Split Output Correctness**
 * **Validates: Requirements 5.1, 5.2, 5.3, 5.5**
 */
class SplitOutputPropertyTest {

    private final EdiParser parser = new EdiParser();
    private final EdiWriter writer = new EdiWriter();

    /**
     * Property 7: Split Output Correctness - ByMessage Strategy
     * 
     * *For any* batch with M messages split by ByMessage strategy,
     * produces exactly M output files, each containing exactly 1 message.
     */
    @Property(tries = 100)
    void byMessageStrategyProducesOneFilePerMessage(
            @ForAll("validBatches") Batch batch
    ) throws IOException {
        SplitStrategy strategy = new SplitStrategy.ByMessage();
        List<WrittenArtifact> artifacts = new ArrayList<>();
        
        OutputTarget target = new OutputTarget.Callback(artifacts::add);
        
        List<WrittenArtifact> result = writer.writeSplit(batch, strategy, target);
        
        // Verify output count matches message count
        assertEquals(batch.messageCount(), result.size(),
                "ByMessage should produce exactly M output files for M messages");
        
        // Verify each output contains exactly 1 message
        for (WrittenArtifact artifact : result) {
            assertEquals(1, artifact.messageCount(),
                    "Each ByMessage output should contain exactly 1 message");
            
            // Verify the output is independently parseable
            assertNotNull(artifact.content(), "Content should not be null");
            Batch parsed = parser.parseBatch(artifact.content());
            assertEquals(1, parsed.messageCount(),
                    "Parsed output should contain exactly 1 message");
        }
    }

    /**
     * Property 7: Split Output Correctness - ByMessageType Strategy
     * 
     * *For any* batch split by ByMessageType strategy,
     * produces exactly |distinct types| files, each containing only messages of that type.
     */
    @Property(tries = 100)
    void byMessageTypeStrategyGroupsCorrectly(
            @ForAll("validBatches") Batch batch
    ) throws IOException {
        SplitStrategy strategy = new SplitStrategy.ByMessageType();
        List<WrittenArtifact> artifacts = new ArrayList<>();
        
        OutputTarget target = new OutputTarget.Callback(artifacts::add);
        
        List<WrittenArtifact> result = writer.writeSplit(batch, strategy, target);
        
        // Count distinct message types in original batch
        Set<String> distinctTypes = batch.getMessages().stream()
                .map(Message::getMessageType)
                .collect(Collectors.toSet());
        
        // Verify output count matches distinct type count
        assertEquals(distinctTypes.size(), result.size(),
                "ByMessageType should produce exactly |distinct types| output files");
        
        // Verify each output contains only messages of one type
        for (WrittenArtifact artifact : result) {
            assertNotNull(artifact.content(), "Content should not be null");
            Batch parsed = parser.parseBatch(artifact.content());
            
            // All messages in this output should have the same type
            Set<String> typesInOutput = parsed.getMessages().stream()
                    .map(Message::getMessageType)
                    .collect(Collectors.toSet());
            
            assertEquals(1, typesInOutput.size(),
                    "Each ByMessageType output should contain only one message type");
        }
    }

    /**
     * Property 7: Split Output Correctness - ByRoutingKey Strategy
     * 
     * *For any* batch split by ByRoutingKey strategy,
     * produces exactly |distinct values of key| files, grouped correctly.
     */
    @Property(tries = 100)
    void byRoutingKeyStrategyGroupsCorrectly(
            @ForAll("batchesWithRoutingKeys") Batch batch
    ) throws IOException {
        String keyName = "receiverId";
        SplitStrategy strategy = new SplitStrategy.ByRoutingKey(keyName);
        List<WrittenArtifact> artifacts = new ArrayList<>();
        
        OutputTarget target = new OutputTarget.Callback(artifacts::add);
        
        List<WrittenArtifact> result = writer.writeSplit(batch, strategy, target);
        
        // Count distinct routing key values in original batch
        Set<String> distinctKeys = batch.getMessages().stream()
                .map(m -> m.getRoutingKeys().getOrDefault(keyName, "unknown"))
                .collect(Collectors.toSet());
        
        // Verify output count matches distinct key count
        assertEquals(distinctKeys.size(), result.size(),
                "ByRoutingKey should produce exactly |distinct key values| output files");
        
        // Verify each output is independently parseable
        for (WrittenArtifact artifact : result) {
            assertNotNull(artifact.content(), "Content should not be null");
            Batch parsed = parser.parseBatch(artifact.content());
            assertTrue(parsed.messageCount() > 0,
                    "Each output should contain at least one message");
        }
    }

    /**
     * Property 7: Split Output Correctness - Each output is independently valid
     * 
     * *For any* batch split by any strategy, each split message SHALL be
     * independently parseable and valid.
     */
    @Property(tries = 100)
    void eachSplitOutputIsIndependentlyValid(
            @ForAll("validBatches") Batch batch,
            @ForAll("splitStrategies") SplitStrategy strategy
    ) throws IOException {
        List<WrittenArtifact> artifacts = new ArrayList<>();
        OutputTarget target = new OutputTarget.Callback(artifacts::add);
        
        List<WrittenArtifact> result = writer.writeSplit(batch, strategy, target);
        
        // Verify each output is independently parseable
        for (WrittenArtifact artifact : result) {
            assertNotNull(artifact.content(), "Content should not be null");
            
            // Should not throw exception
            Batch parsed = parser.parseBatch(artifact.content());
            
            // Should have valid structure
            assertNotNull(parsed, "Parsed batch should not be null");
            assertTrue(parsed.messageCount() > 0,
                    "Each output should contain at least one message");
            
            // Each message should have valid structure
            for (Message message : parsed.getMessages()) {
                assertNotNull(message.getMessageType(),
                        "Message type should not be null");
            }
        }
    }

    /**
     * Property 7: Split Output Correctness - Total message count preserved
     * 
     * *For any* batch split by any strategy, the sum of messages across all
     * outputs should equal the original message count.
     */
    @Property(tries = 100)
    void totalMessageCountPreserved(
            @ForAll("validBatches") Batch batch,
            @ForAll("splitStrategies") SplitStrategy strategy
    ) throws IOException {
        List<WrittenArtifact> artifacts = new ArrayList<>();
        OutputTarget target = new OutputTarget.Callback(artifacts::add);
        
        List<WrittenArtifact> result = writer.writeSplit(batch, strategy, target);
        
        // Sum up message counts from all outputs
        int totalMessages = 0;
        for (WrittenArtifact artifact : result) {
            Batch parsed = parser.parseBatch(artifact.content());
            totalMessages += parsed.messageCount();
        }
        
        assertEquals(batch.messageCount(), totalMessages,
                "Total message count across all outputs should equal original");
    }

    /**
     * Property 7: Split Output Correctness - Message content preserved
     * 
     * *For any* batch split by any strategy, the content of each message
     * should be preserved in the split output.
     */
    @Property(tries = 100)
    void messageContentPreserved(
            @ForAll("validBatches") Batch batch
    ) throws IOException {
        SplitStrategy strategy = new SplitStrategy.ByMessage();
        List<WrittenArtifact> artifacts = new ArrayList<>();
        OutputTarget target = new OutputTarget.Callback(artifacts::add);
        
        List<WrittenArtifact> result = writer.writeSplit(batch, strategy, target);
        
        // Verify each message's content is preserved
        for (int i = 0; i < batch.messageCount(); i++) {
            Message original = batch.getMessages().get(i);
            
            // Find the corresponding artifact
            WrittenArtifact artifact = result.get(i);
            Batch parsed = parser.parseBatch(artifact.content());
            Message reparsed = parsed.getMessages().get(0);
            
            // Verify message type
            assertEquals(original.getMessageType(), reparsed.getMessageType(),
                    "Message type should be preserved");
            
            // Verify segment count
            assertEquals(original.getAllSegments().size(), reparsed.getAllSegments().size(),
                    "Segment count should be preserved");
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
    Arbitrary<Batch> batchesWithRoutingKeys() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                batchIds(),
                messagesWithRoutingKeys().list().ofMinSize(1).ofMaxSize(5)
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
    Arbitrary<SplitStrategy> splitStrategies() {
        return Arbitraries.oneOf(
                Arbitraries.just(new SplitStrategy.ByMessage()),
                Arbitraries.just(new SplitStrategy.ByMessageType())
        );
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

    private Arbitrary<Message> messagesWithRoutingKeys() {
        return Combinators.combine(
                messageTypes(),
                validSegmentLists(),
                routingKeyValues()
        ).as((type, segments, routingValue) -> {
            List<SegmentOrGroup> content = new ArrayList<>(segments);
            Map<String, String> routingKeys = Map.of("receiverId", routingValue);
            return new Message(type, 0, "1", content, routingKeys, null, null);
        });
    }

    private Arbitrary<List<Message>> validMessageLists() {
        return validMessages().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<List<Segment>> validSegmentLists() {
        return validSegments().list().ofMinSize(1).ofMaxSize(5);
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
        return Arbitraries.oneOf(
                safeElementValues().map(Element::of),
                safeElementValues().list().ofMinSize(2).ofMaxSize(3).map(Element::of)
        );
    }

    private Arbitrary<String> messageTypes() {
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC", "DELIVR", "PRICAT");
    }

    private Arbitrary<String> segmentTags() {
        return Arbitraries.of("CLO", "OLD", "ODD", "CLD", "DNA", "PYT", "CRF");
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

    private Arbitrary<String> routingKeyValues() {
        return Arbitraries.of("RECV001", "RECV002", "RECV003");
    }

    private Arbitrary<String> safeElementValues() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(10);
    }
}
