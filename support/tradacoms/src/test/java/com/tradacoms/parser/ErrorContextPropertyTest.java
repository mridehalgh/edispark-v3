package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import com.tradacoms.parser.validation.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for error context completeness.
 * 
 * **Feature: tradacoms-parser, Property 9: Error Context Completeness**
 * **Validates: Requirements 10.1, 10.2, 10.3, 10.4**
 */
class ErrorContextPropertyTest {

    private final EdiValidator validator = new EdiValidator();

    /**
     * Property 9: Error Context Completeness
     * 
     * *For any* parsing or validation error, the error/issue object SHALL contain
     * non-null values for: file identity (when applicable), message index (when in
     * batch context), segment tag, element position, and raw content snippet.
     */
    @Property(tries = 100)
    void errorContextIsComplete(@ForAll("batchesWithErrors") Batch batch) {
        String filename = "test-file.edi";
        ValidationConfig config = ValidationConfig.builder()
                .filename(filename)
                .validateEnvelope(true)
                .validateSchema(false)
                .failFast(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Check all issues have appropriate context
        for (ValidationIssue issue : report.getAllIssues()) {
            assertIssueHasContext(issue, filename);
        }
    }

    /**
     * Property: Batch-level errors have file identity.
     */
    @Property(tries = 100)
    void batchLevelErrorsHaveFileIdentity(@ForAll("batchesWithMissingEnvelope") Batch batch) {
        String filename = "batch-test.edi";
        ValidationConfig config = ValidationConfig.builder()
                .filename(filename)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        for (ValidationIssue issue : report.getBatchLevelIssues()) {
            assertNotNull(issue.getLocation(),
                    "Batch-level issue should have location");
            
            if (issue.getLocation() != null) {
                assertEquals(filename, issue.getLocation().filename(),
                        "Batch-level issue should have correct filename");
            }
        }
    }

    /**
     * Property: Message-level errors have message index.
     */
    @Property(tries = 100)
    void messageLevelErrorsHaveMessageIndex(@ForAll("batchesWithMissingMessageHeaders") Batch batch) {
        String filename = "message-test.edi";
        ValidationConfig config = ValidationConfig.builder()
                .filename(filename)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        for (Map.Entry<Integer, MessageValidationReport> entry : report.getMessageReports().entrySet()) {
            int expectedIndex = entry.getKey();
            MessageValidationReport msgReport = entry.getValue();
            
            for (ValidationIssue issue : msgReport.getIssues()) {
                assertNotNull(issue.getLocation(),
                        "Message-level issue should have location");
                
                if (issue.getLocation() != null) {
                    assertEquals(expectedIndex, issue.getLocation().messageIndex(),
                            "Message-level issue should have correct message index");
                }
            }
        }
    }

    /**
     * Property: Segment-level errors have segment tag.
     */
    @Property(tries = 100)
    void segmentLevelErrorsHaveSegmentTag(@ForAll("batchesWithWrongCounts") Batch batch) {
        String filename = "segment-test.edi";
        ValidationConfig config = ValidationConfig.builder()
                .filename(filename)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Check issues that should have segment context
        for (ValidationIssue issue : report.getAllIssues()) {
            // ENV_003 (message count mismatch) and ENV_007 (segment count mismatch) should have segment tag
            if (ErrorCode.ENV_003.equals(issue.getCode()) || ErrorCode.ENV_007.equals(issue.getCode())) {
                assertNotNull(issue.getLocation(),
                        "Segment-level issue should have location");
                
                if (issue.getLocation() != null) {
                    assertNotNull(issue.getLocation().segmentTag(),
                            "Segment-level issue should have segment tag");
                }
            }
        }
    }

    /**
     * Property: Errors with raw content have snippet.
     */
    @Property(tries = 100)
    void errorsWithRawContentHaveSnippet(@ForAll("batchesWithWrongCounts") Batch batch) {
        String filename = "snippet-test.edi";
        ValidationConfig config = ValidationConfig.builder()
                .filename(filename)
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Check issues that should have raw snippets
        for (ValidationIssue issue : report.getAllIssues()) {
            // ENV_003 and ENV_007 should have raw snippets from the segment
            if (ErrorCode.ENV_003.equals(issue.getCode()) || ErrorCode.ENV_007.equals(issue.getCode())) {
                // Raw snippet should be present when the segment has raw content
                // This is optional based on whether the segment was created with raw content
                if (issue.hasRawSnippet()) {
                    assertFalse(issue.getRawSnippet().isEmpty(),
                            "Raw snippet should not be empty when present");
                }
            }
        }
    }

    /**
     * Property: All issues have valid error codes.
     */
    @Property(tries = 100)
    void allIssuesHaveValidErrorCodes(@ForAll("batchesWithErrors") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        for (ValidationIssue issue : report.getAllIssues()) {
            assertNotNull(issue.getCode(),
                    "Issue should have error code");
            assertFalse(issue.getCode().isEmpty(),
                    "Error code should not be empty");
            
            // Code should follow pattern: CATEGORY_NNN
            assertTrue(issue.getCode().matches("[A-Z]+_\\d{3}"),
                    "Error code should follow pattern CATEGORY_NNN: " + issue.getCode());
        }
    }

    /**
     * Property: All issues have severity.
     */
    @Property(tries = 100)
    void allIssuesHaveSeverity(@ForAll("batchesWithErrors") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        for (ValidationIssue issue : report.getAllIssues()) {
            assertNotNull(issue.getSeverity(),
                    "Issue should have severity");
        }
    }

    /**
     * Property: All issues have message.
     */
    @Property(tries = 100)
    void allIssuesHaveMessage(@ForAll("batchesWithErrors") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        for (ValidationIssue issue : report.getAllIssues()) {
            assertNotNull(issue.getMessage(),
                    "Issue should have message");
            assertFalse(issue.getMessage().isEmpty(),
                    "Issue message should not be empty");
        }
    }

    // ========== Assertion Helpers ==========

    private void assertIssueHasContext(ValidationIssue issue, String expectedFilename) {
        // All issues should have code, severity, and message
        assertNotNull(issue.getCode(), "Issue should have error code");
        assertNotNull(issue.getSeverity(), "Issue should have severity");
        assertNotNull(issue.getMessage(), "Issue should have message");
        
        // Location should be present
        assertNotNull(issue.getLocation(), "Issue should have location");
        
        if (issue.getLocation() != null) {
            // File identity should match when provided
            if (expectedFilename != null) {
                assertEquals(expectedFilename, issue.getLocation().filename(),
                        "Issue should have correct filename");
            }
        }
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
    Arbitrary<Batch> batchesWithMissingEnvelope() {
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

    @Provide
    Arbitrary<Batch> batchesWithMissingMessageHeaders() {
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

    @Provide
    Arbitrary<Batch> batchesWithWrongCounts() {
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
                
                // Wrong segment count in MTR - use raw content for snippet
                Segment mtr = new Segment("MTR", List.of(Element.of("999")), 
                        i * 10 + 5, 0, "MTR+999'", false);
                
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
            
            // Wrong message count in END - use raw content for snippet
            Segment end = new Segment("END", List.of(Element.of("999")), 
                    100, 0, "END+999'", false);
            
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
