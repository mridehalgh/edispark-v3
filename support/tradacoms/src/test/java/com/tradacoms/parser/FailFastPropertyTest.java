package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import com.tradacoms.parser.validation.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for fail-fast and continue-on-error validation behavior.
 * 
 * **Feature: tradacoms-parser, Property 6: Fail-Fast Behavior**
 * **Validates: Requirements 4.4, 4.5, 9.7**
 */
class FailFastPropertyTest {

    private final EdiValidator validator = new EdiValidator();

    /**
     * Property 6: Fail-Fast Behavior
     * 
     * *For any* batch with N errors (N > 0) validated in fail-fast mode,
     * the validation report SHALL contain at most 1 error.
     */
    @Property(tries = 100)
    void failFastStopsAtFirstError(@ForAll("batchesWithErrors") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .failFast(true)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // In fail-fast mode, should have at most 1 error
        int errorCount = report.getErrorCount();
        assertTrue(errorCount <= 1,
                "Fail-fast mode should stop at first error, but found " + errorCount + " errors");
        
        // If there are errors, status should be FAIL
        if (errorCount > 0) {
            assertEquals(ValidationStatus.FAIL, report.getOverallStatus(),
                    "Status should be FAIL when there are errors");
        }
    }

    /**
     * Property: Continue-on-error respects maxIssues limit.
     * 
     * *For any* batch validated in continue-on-error mode with maxIssues=K,
     * the report SHALL contain at most K issues.
     */
    @Property(tries = 100)
    void continueOnErrorRespectsMaxIssues(
            @ForAll("batchesWithManyErrors") Batch batch,
            @ForAll @IntRange(min = 1, max = 10) int maxIssues
    ) {
        ValidationConfig config = ValidationConfig.builder()
                .failFast(false)
                .maxIssues(maxIssues)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Should have at most maxIssues issues
        assertTrue(report.getTotalIssueCount() <= maxIssues,
                "Should have at most " + maxIssues + " issues, but found " + report.getTotalIssueCount());
    }

    /**
     * Property: Non-fail-fast mode collects multiple errors.
     */
    @Property(tries = 100)
    void nonFailFastCollectsMultipleErrors(@ForAll("batchesWithManyErrors") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .failFast(false)
                .maxIssues(Integer.MAX_VALUE)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Count expected errors from the batch structure
        int expectedMinErrors = countExpectedErrors(batch);
        
        // Should collect all errors (or at least more than 1 if there are multiple)
        if (expectedMinErrors > 1) {
            assertTrue(report.getTotalIssueCount() > 1,
                    "Non-fail-fast mode should collect multiple errors when present");
        }
    }

    /**
     * Property: Fail-fast with no errors produces PASS status.
     */
    @Property(tries = 100)
    void failFastWithNoErrorsProducesPass(@ForAll("validBatches") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .failFast(true)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Valid batch should pass
        assertEquals(ValidationStatus.PASS, report.getOverallStatus(),
                "Valid batch should pass validation");
        assertEquals(0, report.getErrorCount(),
                "Valid batch should have no errors");
    }

    /**
     * Property: maxIssues=1 produces at most 1 issue.
     */
    @Property(tries = 100)
    void maxIssuesOneProducesAtMostOneIssue(@ForAll("batchesWithErrors") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .failFast(false)
                .maxIssues(1)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Should have at most 1 issue collected
        assertTrue(report.getTotalIssueCount() <= 1,
                "maxIssues=1 should produce at most 1 issue");
    }

    // ========== Helper Methods ==========

    private int countExpectedErrors(Batch batch) {
        int errors = 0;
        
        // Missing STX
        if (batch.getRawHeader() == null) {
            errors++;
        }
        
        // Missing END
        if (batch.getRawTrailer() == null) {
            errors++;
        }
        
        // Wrong message count in END
        if (batch.getRawTrailer() != null && !batch.getRawTrailer().elements().isEmpty()) {
            try {
                int declared = Integer.parseInt(batch.getRawTrailer().getElementValue(0));
                if (declared != batch.messageCount()) {
                    errors++;
                }
            } catch (NumberFormatException e) {
                // Non-numeric is a warning, not error
            }
        }
        
        // Missing MHD/MTR in messages
        for (Message msg : batch.getMessages()) {
            if (msg.getHeader() == null) {
                errors++;
            }
            if (msg.getTrailer() == null) {
                errors++;
            }
            // Wrong segment count in MTR
            if (msg.getTrailer() != null && !msg.getTrailer().elements().isEmpty()) {
                try {
                    int declared = Integer.parseInt(msg.getTrailer().getElementValue(0));
                    int actual = msg.getAllSegments().size() + 2; // +2 for MHD/MTR
                    if (declared != actual) {
                        errors++;
                    }
                } catch (NumberFormatException e) {
                    // Non-numeric is a warning
                }
            }
        }
        
        return errors;
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<Batch> batchesWithErrors() {
        return Arbitraries.oneOf(
                batchesWithMissingEnvelope(),
                batchesWithMissingMessageHeaders(),
                batchesWithWrongCounts()
        );
    }

    @Provide
    Arbitrary<Batch> batchesWithManyErrors() {
        // Create batches with multiple types of errors
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                Arbitraries.integers().between(2, 5)
        ).as((sender, receiver, messageCount) -> {
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < messageCount; i++) {
                // Messages without MHD/MTR (2 errors per message)
                messages.add(new Message(
                        "CREDIT",
                        i,
                        String.valueOf(i + 1),
                        List.of(Segment.of("CLO", List.of(Element.of("TEST")))),
                        Map.of(),
                        null, // No MHD
                        null  // No MTR
                ));
            }
            
            // Wrong message count in END
            Segment end = Segment.of("END", List.of(Element.of("999")));
            
            return new Batch(
                    "BATCH001",
                    sender,
                    receiver,
                    Instant.now(),
                    messages,
                    Segment.of("STX", List.of(Element.of("ANAA"), Element.of(sender), Element.of(receiver))),
                    end,
                    null
            );
        });
    }

    private Arbitrary<Batch> batchesWithMissingEnvelope() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                validMessageLists()
        ).as((sender, receiver, messages) -> {
            List<Message> messagesWithHeaders = createMessagesWithHeaders(messages);
            
            // Missing STX and/or END
            return new Batch(
                    "BATCH001",
                    sender,
                    receiver,
                    Instant.now(),
                    messagesWithHeaders,
                    null, // No STX
                    null, // No END
                    null
            );
        });
    }

    private Arbitrary<Batch> batchesWithMissingMessageHeaders() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                validMessageLists()
        ).as((sender, receiver, messages) -> {
            // Messages without MHD/MTR
            List<Message> messagesWithoutHeaders = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                messagesWithoutHeaders.add(new Message(
                        m.getMessageType(),
                        i,
                        String.valueOf(i + 1),
                        m.getContent(),
                        m.getRoutingKeys(),
                        null, // No MHD
                        null  // No MTR
                ));
            }
            
            Segment stx = Segment.of("STX", List.of(
                    Element.of("ANAA"),
                    Element.of(sender),
                    Element.of(receiver)
            ));
            
            Segment end = Segment.of("END", List.of(
                    Element.of(String.valueOf(messagesWithoutHeaders.size()))
            ));
            
            return new Batch(
                    "BATCH001",
                    sender,
                    receiver,
                    Instant.now(),
                    messagesWithoutHeaders,
                    stx,
                    end,
                    null
            );
        });
    }

    private Arbitrary<Batch> batchesWithWrongCounts() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                validMessageLists()
        ).as((sender, receiver, messages) -> {
            List<Message> messagesWithHeaders = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                
                Segment mhd = Segment.of("MHD", List.of(
                        Element.of(String.valueOf(i + 1)),
                        Element.of(List.of(m.getMessageType(), "9"))
                ));
                
                // Wrong segment count in MTR
                Segment mtr = Segment.of("MTR", List.of(Element.of("999")));
                
                messagesWithHeaders.add(new Message(
                        m.getMessageType(),
                        i,
                        String.valueOf(i + 1),
                        m.getContent(),
                        m.getRoutingKeys(),
                        mhd,
                        mtr
                ));
            }
            
            Segment stx = Segment.of("STX", List.of(
                    Element.of("ANAA"),
                    Element.of(sender),
                    Element.of(receiver)
            ));
            
            // Wrong message count in END
            Segment end = Segment.of("END", List.of(Element.of("999")));
            
            return new Batch(
                    "BATCH001",
                    sender,
                    receiver,
                    Instant.now(),
                    messagesWithHeaders,
                    stx,
                    end,
                    null
            );
        });
    }

    @Provide
    Arbitrary<Batch> validBatches() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                validMessageLists()
        ).as((sender, receiver, messages) -> {
            List<Message> messagesWithHeaders = createMessagesWithHeaders(messages);
            
            Segment stx = Segment.of("STX", List.of(
                    Element.of("ANAA"),
                    Element.of(sender),
                    Element.of(receiver),
                    Element.of("240115"),
                    Element.of("1200")
            ));
            
            Segment end = Segment.of("END", List.of(
                    Element.of(String.valueOf(messagesWithHeaders.size()))
            ));
            
            return new Batch(
                    "BATCH001",
                    sender,
                    receiver,
                    Instant.now(),
                    messagesWithHeaders,
                    stx,
                    end,
                    null
            );
        });
    }

    private List<Message> createMessagesWithHeaders(List<Message> messages) {
        List<Message> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            
            Segment mhd = Segment.of("MHD", List.of(
                    Element.of(String.valueOf(i + 1)),
                    Element.of(List.of(m.getMessageType(), "9"))
            ));
            
            int segmentCount = m.getAllSegments().size() + 2;
            Segment mtr = Segment.of("MTR", List.of(Element.of(String.valueOf(segmentCount))));
            
            result.add(new Message(
                    m.getMessageType(),
                    i,
                    String.valueOf(i + 1),
                    m.getContent(),
                    m.getRoutingKeys(),
                    mhd,
                    mtr
            ));
        }
        return result;
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
        return Arbitraries.of("CLO", "OLD", "ODD", "CLD", "DNA", "PYT");
    }

    private Arbitrary<String> senderIds() {
        return Arbitraries.strings().numeric().ofLength(13);
    }

    private Arbitrary<String> receiverIds() {
        return Arbitraries.strings().numeric().ofLength(13);
    }

    private Arbitrary<String> safeElementValues() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(10);
    }
}
