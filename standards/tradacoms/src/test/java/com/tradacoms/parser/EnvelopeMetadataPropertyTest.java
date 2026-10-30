package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import net.jqwik.api.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for envelope metadata extraction in TRADACOMS batch parsing.
 * 
 * **Feature: tradacoms-parser, Property 13: Envelope Metadata Extraction**
 * **Validates: Requirements 2.2, 1.3**
 */
class EnvelopeMetadataPropertyTest {

    private final EdiParser parser = new EdiParser();

    /**
     * Property 13: Envelope Metadata Extraction
     * 
     * *For any* batch with STX segment containing sender/receiver/timestamp fields,
     * the parsed Batch object SHALL expose these values accurately, matching the
     * raw segment content.
     */
    @Property(tries = 100)
    void senderIdMatchesStxContent(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        assertEquals(generated.senderId(), batch.getSenderId(),
                "Sender ID should match STX content");
    }

    @Property(tries = 100)
    void receiverIdMatchesStxContent(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        assertEquals(generated.receiverId(), batch.getReceiverId(),
                "Receiver ID should match STX content");
    }

    @Property(tries = 100)
    void batchIdMatchesStxContent(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        assertEquals(generated.batchId(), batch.getBatchId(),
                "Batch ID (transmission reference) should match STX content");
    }

    @Property(tries = 100)
    void timestampMatchesStxContent(
            @ForAll("validBatchesWithTimestamp") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        assertNotNull(batch.getCreationTimestamp(),
                "Timestamp should be extracted from STX");
        
        // Verify the timestamp matches the generated date/time
        Instant expected = generated.expectedTimestamp();
        assertEquals(expected, batch.getCreationTimestamp(),
                "Timestamp should match STX date/time content");
    }

    /**
     * Property: Raw header segment is preserved.
     */
    @Property(tries = 100)
    void rawHeaderIsPreserved(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        assertNotNull(batch.getRawHeader(), "Raw header should be preserved");
        assertEquals("STX", batch.getRawHeader().tag(), "Raw header should be STX segment");
    }

    /**
     * Property: Raw trailer segment is preserved.
     */
    @Property(tries = 100)
    void rawTrailerIsPreserved(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        assertNotNull(batch.getRawTrailer(), "Raw trailer should be preserved");
        assertEquals("END", batch.getRawTrailer().tag(), "Raw trailer should be END segment");
    }

    /**
     * Property: Sender ID from STX element matches parsed value.
     */
    @Property(tries = 100)
    void senderIdFromRawHeaderMatchesParsedValue(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        // Get sender from raw header (element index 1, first component)
        Segment stx = batch.getRawHeader();
        String rawSenderId = stx.elements().get(1).getValue();
        
        assertEquals(rawSenderId, batch.getSenderId(),
                "Sender ID should match raw STX element");
    }

    /**
     * Property: Receiver ID from STX element matches parsed value.
     */
    @Property(tries = 100)
    void receiverIdFromRawHeaderMatchesParsedValue(
            @ForAll("validBatches") GeneratedBatch generated
    ) {
        Batch batch = parser.parseBatch(generated.tradacomsString());
        
        // Get receiver from raw header (element index 2, first component)
        Segment stx = batch.getRawHeader();
        String rawReceiverId = stx.elements().get(2).getValue();
        
        assertEquals(rawReceiverId, batch.getReceiverId(),
                "Receiver ID should match raw STX element");
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<GeneratedBatch> validBatches() {
        return Combinators.combine(
                senderIds(),
                receiverIds(),
                batchIds(),
                dates(),
                times()
        ).as((sender, receiver, batchId, date, time) -> {
            String dateStr = String.format("%02d%02d%02d", 
                    date.getYear() % 100, date.getMonthValue(), date.getDayOfMonth());
            String timeStr = String.format("%02d%02d%02d",
                    time.getHour(), time.getMinute(), time.getSecond());
            
            StringBuilder sb = new StringBuilder();
            sb.append("STX=ANAA:1+").append(sender).append(":SENDER NAME+")
              .append(receiver).append(":RECEIVER NAME+")
              .append(dateStr).append(":").append(timeStr).append("+")
              .append(batchId).append("'");
            sb.append("MHD=1+CREDIT:9'CLO=123456'MTR=2'");
            sb.append("END=1'");
            
            Instant timestamp = date.atTime(time).toInstant(ZoneOffset.UTC);
            
            return new GeneratedBatch(sb.toString(), sender, receiver, batchId, timestamp);
        });
    }

    @Provide
    Arbitrary<GeneratedBatch> validBatchesWithTimestamp() {
        return validBatches();
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
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(14);
    }

    private Arbitrary<LocalDate> dates() {
        // Generate dates in the range 2020-2029 (will be formatted as YYMMDD)
        return Arbitraries.integers().between(2020, 2029)
                .flatMap(year -> Arbitraries.integers().between(1, 12)
                        .flatMap(month -> Arbitraries.integers().between(1, 28)
                                .map(day -> LocalDate.of(year, month, day))));
    }

    private Arbitrary<LocalTime> times() {
        return Arbitraries.integers().between(0, 23)
                .flatMap(hour -> Arbitraries.integers().between(0, 59)
                        .flatMap(minute -> Arbitraries.integers().between(0, 59)
                                .map(second -> LocalTime.of(hour, minute, second))));
    }

    // ========== Record Types ==========

    record GeneratedBatch(
            String tradacomsString,
            String senderId,
            String receiverId,
            String batchId,
            Instant expectedTimestamp
    ) {}
}
