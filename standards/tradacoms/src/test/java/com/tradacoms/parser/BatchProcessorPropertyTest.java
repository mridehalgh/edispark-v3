package com.tradacoms.parser;

import com.tradacoms.parser.batch.*;
import com.tradacoms.parser.model.*;
import com.tradacoms.parser.validation.ValidationConfig;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for batch processor result completeness.
 * 
 * **Feature: tradacoms-parser, Property 11: Batch Processor Result Completeness**
 * **Validates: Requirements 7.2, 7.3, 7.5, 7.6**
 */
class BatchProcessorPropertyTest {

    private final BatchProcessor processor = new BatchProcessor();
    private final EdiWriter writer = new EdiWriter();

    /**
     * Property 11: Batch Processor Result Completeness - Result count matches input count
     * 
     * *For any* list of N input files processed by BatchProcessor,
     * the result SHALL contain exactly N FileResult objects with correlation IDs.
     */
    @Property(tries = 100)
    void resultCountMatchesInputCount(
            @ForAll("validInputSources") List<InputSource> inputs
    ) {
        BatchProcessConfig config = BatchProcessConfig.builder()
                .continueOnFileError(true)
                .build();

        BatchProcessingResult result = processor.process(inputs, config);

        assertEquals(inputs.size(), result.getFileCount(),
                "Result count should match input count");

        // Each result should have a non-null correlation ID
        for (FileResult fr : result.getFileResults()) {
            assertNotNull(fr.getCorrelationId(),
                    "Each file result should have a correlation ID");
            assertFalse(fr.getCorrelationId().isEmpty(),
                    "Correlation ID should not be empty");
        }
    }

    /**
     * Property 11: Batch Processor Result Completeness - Message counts sum correctly
     * 
     * *For any* list of input files, the sum of message counts across FileResults
     * SHALL equal total messages parsed.
     */
    @Property(tries = 100)
    void messageCountsSumCorrectly(
            @ForAll("validInputSources") List<InputSource> inputs
    ) {
        BatchProcessConfig config = BatchProcessConfig.builder()
                .continueOnFileError(true)
                .build();

        BatchProcessingResult result = processor.process(inputs, config);

        // Sum message counts from file results
        int sumFromFileResults = result.getFileResults().stream()
                .mapToInt(FileResult::getMessageCount)
                .sum();

        // Compare with summary
        assertEquals(result.getSummary().getTotalMessages(), sumFromFileResults,
                "Summary total messages should equal sum of file result message counts");

        // Also verify valid + invalid + skipped = total
        int validCount = result.getSummary().getValidMessages();
        int invalidCount = result.getSummary().getInvalidMessages();
        int skippedCount = result.getSummary().getSkippedMessages();
        
        assertEquals(sumFromFileResults, validCount + invalidCount + skippedCount,
                "Valid + invalid + skipped should equal total messages");
    }

    /**
     * Property 11: Batch Processor Result Completeness - Order preserved with deterministic flag
     * 
     * When deterministicOutputOrder=true, result order SHALL match input order.
     */
    @Property(tries = 100)
    void orderPreservedWhenDeterministic(
            @ForAll("validInputSources") List<InputSource> inputs
    ) {
        BatchProcessConfig config = BatchProcessConfig.builder()
                .deterministicOutputOrder(true)
                .continueOnFileError(true)
                .threadPoolSize(4)  // Use multiple threads to test ordering
                .build();

        BatchProcessingResult result = processor.process(inputs, config);

        // Verify order matches input order
        assertEquals(inputs.size(), result.getFileCount(),
                "Result count should match input count");

        for (int i = 0; i < inputs.size(); i++) {
            FileResult fr = result.getFileResults().get(i);
            String expectedFilename = inputs.get(i).getId();
            
            assertEquals(expectedFilename, fr.getFilename(),
                    "File result at index " + i + " should match input filename");
        }
    }

    /**
     * Property 11: Batch Processor Result Completeness - Each message has unique ID
     * 
     * *For any* processed batch, each message result SHALL have a message ID
     * derived from control refs + index.
     */
    @Property(tries = 100)
    void eachMessageHasUniqueId(
            @ForAll("validInputSources") List<InputSource> inputs
    ) {
        BatchProcessConfig config = BatchProcessConfig.builder()
                .continueOnFileError(true)
                .build();

        BatchProcessingResult result = processor.process(inputs, config);

        for (FileResult fr : result.getFileResults()) {
            Set<String> messageIds = new HashSet<>();
            
            for (MessageResult mr : fr.getMessageResults()) {
                assertNotNull(mr.getMessageId(),
                        "Message ID should not be null");
                assertFalse(mr.getMessageId().isEmpty(),
                        "Message ID should not be empty");
                
                // Message IDs should be unique within a file
                assertTrue(messageIds.add(mr.getMessageId()),
                        "Message IDs should be unique within a file");
            }
        }
    }

    /**
     * Property 11: Batch Processor Result Completeness - File status consistency
     * 
     * *For any* processed batch, file status should be consistent with message statuses.
     */
    @Property(tries = 100)
    void fileStatusConsistentWithMessageStatuses(
            @ForAll("validInputSources") List<InputSource> inputs
    ) {
        BatchProcessConfig config = BatchProcessConfig.builder()
                .continueOnFileError(true)
                .build();

        BatchProcessingResult result = processor.process(inputs, config);

        for (FileResult fr : result.getFileResults()) {
            if (fr.getStatus() == FileStatus.SUCCESS) {
                // All messages should be valid
                boolean allValid = fr.getMessageResults().stream()
                        .allMatch(mr -> mr.getStatus() == MessageStatus.VALID);
                assertTrue(allValid || fr.getMessageCount() == 0,
                        "SUCCESS status should mean all messages are valid");
            }
            
            if (fr.getStatus() == FileStatus.PARTIAL) {
                // Should have at least one invalid message
                boolean hasInvalid = fr.getMessageResults().stream()
                        .anyMatch(mr -> mr.getStatus() == MessageStatus.INVALID);
                assertTrue(hasInvalid,
                        "PARTIAL status should have at least one invalid message");
            }
        }
    }

    /**
     * Property 11: Batch Processor Result Completeness - Summary statistics accuracy
     * 
     * *For any* processed batch, summary statistics should accurately reflect file results.
     */
    @Property(tries = 100)
    void summaryStatisticsAccurate(
            @ForAll("validInputSources") List<InputSource> inputs
    ) {
        BatchProcessConfig config = BatchProcessConfig.builder()
                .continueOnFileError(true)
                .build();

        BatchProcessingResult result = processor.process(inputs, config);
        ProcessingSummary summary = result.getSummary();

        // Count file statuses manually
        int successCount = 0;
        int partialCount = 0;
        int failedCount = 0;
        
        for (FileResult fr : result.getFileResults()) {
            switch (fr.getStatus()) {
                case SUCCESS -> successCount++;
                case PARTIAL -> partialCount++;
                case FAILED -> failedCount++;
            }
        }

        assertEquals(successCount, summary.getSuccessfulFiles(),
                "Successful file count should match");
        assertEquals(partialCount, summary.getPartialFiles(),
                "Partial file count should match");
        assertEquals(failedCount, summary.getFailedFiles(),
                "Failed file count should match");
        assertEquals(inputs.size(), summary.getTotalFiles(),
                "Total file count should match input count");
    }

    /**
     * Property 11: Batch Processor Result Completeness - Empty input handling
     * 
     * *For any* empty input list, result should be empty with zero counts.
     */
    @Property(tries = 10)
    void emptyInputProducesEmptyResult() {
        BatchProcessConfig config = BatchProcessConfig.defaults();

        BatchProcessingResult result = processor.process(List.of(), config);

        assertEquals(0, result.getFileCount(),
                "Empty input should produce empty result");
        assertEquals(0, result.getSummary().getTotalFiles(),
                "Summary should show zero files");
        assertEquals(0, result.getSummary().getTotalMessages(),
                "Summary should show zero messages");
    }

    /**
     * Property 11: Batch Processor Result Completeness - Single-threaded vs multi-threaded consistency
     * 
     * *For any* input, single-threaded and multi-threaded processing should produce
     * equivalent results (same file count, same message counts).
     */
    @Property(tries = 50)
    void singleAndMultiThreadedProduceEquivalentResults(
            @ForAll("validInputSources") List<InputSource> inputs
    ) {
        BatchProcessConfig singleThreadConfig = BatchProcessConfig.builder()
                .threadPoolSize(1)
                .deterministicOutputOrder(true)
                .continueOnFileError(true)
                .build();

        BatchProcessConfig multiThreadConfig = BatchProcessConfig.builder()
                .threadPoolSize(4)
                .deterministicOutputOrder(true)
                .continueOnFileError(true)
                .build();

        BatchProcessingResult singleResult = processor.process(inputs, singleThreadConfig);
        BatchProcessingResult multiResult = processor.process(inputs, multiThreadConfig);

        // Same file count
        assertEquals(singleResult.getFileCount(), multiResult.getFileCount(),
                "File count should be same for single and multi-threaded");

        // Same total message count
        assertEquals(singleResult.getSummary().getTotalMessages(),
                multiResult.getSummary().getTotalMessages(),
                "Total message count should be same");

        // Same order when deterministic
        for (int i = 0; i < singleResult.getFileCount(); i++) {
            assertEquals(singleResult.getFileResults().get(i).getFilename(),
                    multiResult.getFileResults().get(i).getFilename(),
                    "File order should be preserved");
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<List<InputSource>> validInputSources() {
        return validStreamSources().list().ofMinSize(1).ofMaxSize(5);
    }

    private Arbitrary<InputSource> validStreamSources() {
        return validBatches().map(batch -> {
            String content = writer.serializeBatch(batch);
            String id = "test-" + UUID.randomUUID().toString().substring(0, 8) + ".edi";
            Supplier<InputStream> supplier = () -> 
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            return new InputSource.StreamSource(id, supplier);
        });
    }

    private Arbitrary<Batch> validBatches() {
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

    private Arbitrary<List<Message>> validMessageLists() {
        return validMessages().list().ofMinSize(1).ofMaxSize(3);
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
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC", "DELIVR");
    }

    private Arbitrary<String> segmentTags() {
        return Arbitraries.of("CLO", "OLD", "ODD", "CLD", "DNA");
    }

    private Arbitrary<String> senderIds() {
        return Arbitraries.strings().numeric().ofLength(13);
    }

    private Arbitrary<String> receiverIds() {
        return Arbitraries.strings().numeric().ofLength(13);
    }

    private Arbitrary<String> batchIds() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8);
    }

    private Arbitrary<String> safeElementValues() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(10);
    }
}
