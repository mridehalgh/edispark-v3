package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import com.tradacoms.parser.validation.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for validation report completeness.
 * 
 * **Feature: tradacoms-parser, Property 5: Validation Report Completeness**
 * **Validates: Requirements 4.1, 4.2, 4.3**
 */
class ValidationReportPropertyTest {

    private final EdiValidator validator = new EdiValidator();

    /**
     * Property 5: Validation Report Completeness
     * 
     * *For any* batch validation, the BatchValidationReport SHALL have:
     * (a) an overall status consistent with per-message statuses,
     * (b) per-message reports for all messages, and
     * (c) issue counts that sum to total issues across all messages.
     */
    @Property(tries = 100)
    void validationReportIsComplete(@ForAll("validBatches") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .validateEnvelope(true)
                .validateSchema(false) // No schema for this test
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // (a) Overall status is consistent with per-message statuses
        assertOverallStatusConsistent(report);

        // (b) Per-message reports exist for all messages
        assertEquals(batch.messageCount(), report.getMessageReports().size(),
                "Should have a report for each message");
        
        for (int i = 0; i < batch.messageCount(); i++) {
            assertTrue(report.getMessageReport(i).isPresent(),
                    "Should have report for message " + i);
        }

        // (c) Issue counts sum correctly
        assertIssueCountsConsistent(report);
    }

    /**
     * Property: Report status reflects presence of errors and warnings.
     */
    @Property(tries = 100)
    void reportStatusReflectsIssues(@ForAll("batchesWithIssues") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Status should be FAIL if there are errors
        if (report.getErrorCount() > 0) {
            assertEquals(ValidationStatus.FAIL, report.getOverallStatus(),
                    "Status should be FAIL when there are errors");
        }
        // Status should be WARN if there are warnings but no errors
        else if (report.getWarningCount() > 0) {
            assertEquals(ValidationStatus.WARN, report.getOverallStatus(),
                    "Status should be WARN when there are warnings but no errors");
        }
        // Status should be PASS if no errors or warnings
        else {
            assertEquals(ValidationStatus.PASS, report.getOverallStatus(),
                    "Status should be PASS when there are no issues");
        }
    }

    /**
     * Property: Issue counts by code sum to total issues.
     */
    @Property(tries = 100)
    void issueCountsByCodeSumCorrectly(@ForAll("validBatches") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Sum of counts by code should equal total issues
        int sumByCode = report.getIssueCountsByCode().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        
        assertEquals(report.getTotalIssueCount(), sumByCode,
                "Sum of issue counts by code should equal total issue count");
    }

    /**
     * Property: Issue counts by severity sum to total issues.
     */
    @Property(tries = 100)
    void issueCountsBySeveritySumCorrectly(@ForAll("validBatches") Batch batch) {
        ValidationConfig config = ValidationConfig.builder()
                .validateEnvelope(true)
                .validateSchema(false)
                .build();

        BatchValidationReport report = validator.validateBatch(batch, config);

        // Sum of counts by severity should equal total issues
        int sumBySeverity = report.getIssueCountsBySeverity().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        
        assertEquals(report.getTotalIssueCount(), sumBySeverity,
                "Sum of issue counts by severity should equal total issue count");
    }

    /**
     * Property: Message reports have correct indices.
     */
    @Property(tries = 100)
    void messageReportsHaveCorrectIndices(@ForAll("validBatches") Batch batch) {
        ValidationConfig config = ValidationConfig.defaults();

        BatchValidationReport report = validator.validateBatch(batch, config);

        for (Map.Entry<Integer, MessageValidationReport> entry : report.getMessageReports().entrySet()) {
            int key = entry.getKey();
            MessageValidationReport msgReport = entry.getValue();
            
            assertEquals(key, msgReport.getMessageIndex(),
                    "Message report index should match map key");
            assertTrue(key >= 0 && key < batch.messageCount(),
                    "Message index should be within valid range");
        }
    }

    // ========== Assertion Helpers ==========

    private void assertOverallStatusConsistent(BatchValidationReport report) {
        boolean hasErrors = report.getBatchLevelIssues().stream()
                .anyMatch(i -> i.getSeverity() == Severity.ERROR);
        boolean hasWarnings = report.getBatchLevelIssues().stream()
                .anyMatch(i -> i.getSeverity() == Severity.WARNING);

        for (MessageValidationReport msgReport : report.getMessageReports().values()) {
            if (msgReport.getStatus() == ValidationStatus.FAIL) {
                hasErrors = true;
            } else if (msgReport.getStatus() == ValidationStatus.WARN) {
                hasWarnings = true;
            }
        }

        ValidationStatus expectedStatus = ValidationStatus.derive(hasErrors, hasWarnings);
        assertEquals(expectedStatus, report.getOverallStatus(),
                "Overall status should be consistent with per-message statuses");
    }

    private void assertIssueCountsConsistent(BatchValidationReport report) {
        // Count all issues manually
        int totalFromBatch = report.getBatchLevelIssues().size();
        int totalFromMessages = report.getMessageReports().values().stream()
                .mapToInt(MessageValidationReport::getTotalIssueCount)
                .sum();
        int expectedTotal = totalFromBatch + totalFromMessages;

        assertEquals(expectedTotal, report.getTotalIssueCount(),
                "Total issue count should equal sum of batch and message issues");

        // Verify counts by severity
        Map<Severity, Integer> expectedBySeverity = new EnumMap<>(Severity.class);
        for (ValidationIssue issue : report.getBatchLevelIssues()) {
            expectedBySeverity.merge(issue.getSeverity(), 1, Integer::sum);
        }
        for (MessageValidationReport msgReport : report.getMessageReports().values()) {
            for (ValidationIssue issue : msgReport.getIssues()) {
                expectedBySeverity.merge(issue.getSeverity(), 1, Integer::sum);
            }
        }

        for (Severity severity : Severity.values()) {
            int expected = expectedBySeverity.getOrDefault(severity, 0);
            int actual = report.getIssueCountsBySeverity().getOrDefault(severity, 0);
            assertEquals(expected, actual,
                    "Issue count for severity " + severity + " should match");
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<Batch> validBatches() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                validMessageLists()
        ).as((sender, receiver, messages) -> {
            // Create proper MHD/MTR segments for each message
            List<Message> messagesWithHeaders = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                Message m = messages.get(i);
                
                // Create MHD segment
                Segment mhd = Segment.of("MHD", List.of(
                        Element.of(String.valueOf(i + 1)),
                        Element.of(List.of(m.getMessageType(), "9"))
                ));
                
                // Count segments for MTR
                int segmentCount = m.getAllSegments().size() + 2; // +2 for MHD and MTR
                Segment mtr = Segment.of("MTR", List.of(Element.of(String.valueOf(segmentCount))));
                
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
            
            // Create STX segment
            Segment stx = Segment.of("STX", List.of(
                    Element.of("ANAA"),
                    Element.of(sender),
                    Element.of(receiver),
                    Element.of("240115"),
                    Element.of("1200")
            ));
            
            // Create END segment with correct message count
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

    @Provide
    Arbitrary<Batch> batchesWithIssues() {
        return Arbitraries.oneOf(
                validBatches(),
                batchesWithMissingHeaders(),
                batchesWithWrongCounts()
        );
    }

    private Arbitrary<Batch> batchesWithMissingHeaders() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                validMessageLists()
        ).as((sender, receiver, messages) -> {
            // Create messages without MHD/MTR
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
