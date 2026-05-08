package com.tradacoms.parser;

import com.tradacoms.parser.model.*;
import com.tradacoms.parser.schema.MessageSchema;
import com.tradacoms.parser.schema.SchemaLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the EdiParser.
 */
class EdiParserTest {

    private final EdiParser parser = new EdiParser();

    @Test
    void testParseSimpleMessage() {
        String input = "MHD=1+CREDIT:9'CLO=123456+Customer Name'MTR=2'";
        
        Message message = parser.parseMessage(input);
        
        assertEquals("CREDIT", message.getMessageType());
        assertEquals(0, message.getMessageIndexInBatch());
        assertEquals("1", message.getMessageControlRef());
        assertNotNull(message.getHeader());
        assertNotNull(message.getTrailer());
        assertEquals("MHD", message.getHeader().tag());
        assertEquals("MTR", message.getTrailer().tag());
        
        // Check content (should have CLO segment)
        List<Segment> allSegments = message.getAllSegments();
        assertEquals(1, allSegments.size());
        assertEquals("CLO", allSegments.get(0).tag());
    }

    @Test
    void testParseMessagePreservesSegmentOrder() {
        String input = "MHD=1+ORDERS:9'CLO=123'OLD=1+PROD1'OLD=2+PROD2'OLD=3+PROD3'MTR=5'";
        
        Message message = parser.parseMessage(input);
        
        List<Segment> segments = message.getAllSegments();
        assertEquals(4, segments.size());
        assertEquals("CLO", segments.get(0).tag());
        assertEquals("OLD", segments.get(1).tag());
        assertEquals("OLD", segments.get(2).tag());
        assertEquals("OLD", segments.get(3).tag());
        
        // Verify element values
        assertEquals("1", segments.get(1).getElementValue(0));
        assertEquals("2", segments.get(2).getElementValue(0));
        assertEquals("3", segments.get(3).getElementValue(0));
    }

    @Test
    void testParseMessageWithCompositeElements() {
        String input = "MHD=1+CREDIT:9'CRF=CN001+260115+260115'MTR=2'";
        
        Message message = parser.parseMessage(input);
        
        List<Segment> segments = message.getAllSegments();
        assertEquals(1, segments.size());
        
        Segment crfSegment = segments.get(0);
        assertEquals("CRF", crfSegment.tag());
        assertEquals(3, crfSegment.elements().size());
        assertEquals("CN001", crfSegment.getElementValue(0));
        assertEquals("260115", crfSegment.getElementValue(1));
        assertEquals("260115", crfSegment.getElementValue(2));
    }

    @Test
    void testParseBatch() {
        String input = 
            "STX=ANAA:1+5000000000000:SENDER+5000000000001:RECEIVER+260115:120000+REF001'" +
            "MHD=1+CREDIT:9'CLO=123456'MTR=2'" +
            "MHD=2+CREDIT:9'CLO=789012'MTR=2'" +
            "END=2'";
        
        Batch batch = parser.parseBatch(input);
        
        assertEquals("5000000000000", batch.getSenderId());
        assertEquals("5000000000001", batch.getReceiverId());
        assertEquals("REF001", batch.getBatchId());
        assertNotNull(batch.getCreationTimestamp());
        assertNotNull(batch.getRawHeader());
        assertNotNull(batch.getRawTrailer());
        assertEquals("STX", batch.getRawHeader().tag());
        assertEquals("END", batch.getRawTrailer().tag());
        
        // Check messages
        assertEquals(2, batch.messageCount());
        
        Message msg1 = batch.getMessages().get(0);
        assertEquals("CREDIT", msg1.getMessageType());
        assertEquals(0, msg1.getMessageIndexInBatch());
        assertEquals("1", msg1.getMessageControlRef());
        
        Message msg2 = batch.getMessages().get(1);
        assertEquals("CREDIT", msg2.getMessageType());
        assertEquals(1, msg2.getMessageIndexInBatch());
        assertEquals("2", msg2.getMessageControlRef());
    }

    @Test
    void testParseBatchWithMessageFiltering() {
        String input = 
            "STX=ANAA:1+SENDER+RECEIVER+260115:120000+REF001'" +
            "MHD=1+ORDERS:9'CLO=123'MTR=2'" +
            "MHD=2+CREDIT:9'CLO=456'MTR=2'" +
            "MHD=3+INVOIC:9'CLO=789'MTR=2'" +
            "END=3'";
        
        ParserConfig config = ParserConfig.builder()
                .messageTypeAllowlist(java.util.Set.of("CREDIT", "INVOIC"))
                .build();
        
        EdiParser filteringParser = new EdiParser(config);
        Batch batch = filteringParser.parseBatch(input);
        
        // Should only include CREDIT and INVOIC messages
        assertEquals(2, batch.messageCount());
        assertEquals("CREDIT", batch.getMessages().get(0).getMessageType());
        assertEquals("INVOIC", batch.getMessages().get(1).getMessageType());
    }

    @Test
    void testParseBatchWithDenylist() {
        String input = 
            "STX=ANAA:1+SENDER+RECEIVER+260115:120000+REF001'" +
            "MHD=1+ORDERS:9'CLO=123'MTR=2'" +
            "MHD=2+CREDIT:9'CLO=456'MTR=2'" +
            "MHD=3+INVOIC:9'CLO=789'MTR=2'" +
            "END=3'";
        
        ParserConfig config = ParserConfig.builder()
                .messageTypeDenylist(java.util.Set.of("ORDERS"))
                .build();
        
        EdiParser filteringParser = new EdiParser(config);
        Batch batch = filteringParser.parseBatch(input);
        
        // Should exclude ORDERS message
        assertEquals(2, batch.messageCount());
        assertEquals("CREDIT", batch.getMessages().get(0).getMessageType());
        assertEquals("INVOIC", batch.getMessages().get(1).getMessageType());
    }

    @Test
    void testParseBatchWithMaxMessages() {
        String input = 
            "STX=ANAA:1+SENDER+RECEIVER+260115:120000+REF001'" +
            "MHD=1+CREDIT:9'CLO=123'MTR=2'" +
            "MHD=2+CREDIT:9'CLO=456'MTR=2'" +
            "MHD=3+CREDIT:9'CLO=789'MTR=2'" +
            "END=3'";
        
        ParserConfig config = ParserConfig.builder()
                .maxMessages(2)
                .build();
        
        EdiParser limitedParser = new EdiParser(config);
        Batch batch = limitedParser.parseBatch(input);
        
        // Should only parse first 2 messages
        assertEquals(2, batch.messageCount());
    }

    @Test
    void testParseDateTime() {
        LocalDate date = EdiParser.parseDate("260115");
        assertNotNull(date);
        assertEquals(2026, date.getYear());
        assertEquals(1, date.getMonthValue());
        assertEquals(15, date.getDayOfMonth());
        
        LocalTime time = EdiParser.parseTime("143052");
        assertNotNull(time);
        assertEquals(14, time.getHour());
        assertEquals(30, time.getMinute());
        assertEquals(52, time.getSecond());
        
        LocalTime shortTime = EdiParser.parseTime("1430");
        assertNotNull(shortTime);
        assertEquals(14, shortTime.getHour());
        assertEquals(30, shortTime.getMinute());
    }

    @Test
    void testMissingSTXThrowsException() {
        String input = "MHD=1+CREDIT:9'CLO=123'MTR=2'END=1'";
        
        ParseException ex = assertThrows(ParseException.class, () -> parser.parseBatch(input));
        assertEquals("ENV_001", ex.getErrorCode());
    }

    @Test
    void testMissingENDThrowsException() {
        String input = "STX=ANAA:1+SENDER+RECEIVER+260115:120000+REF001'MHD=1+CREDIT:9'CLO=123'MTR=2'";
        
        ParseException ex = assertThrows(ParseException.class, () -> parser.parseBatch(input));
        assertEquals("ENV_002", ex.getErrorCode());
    }

    @Test
    void testGroupDetectionWithHeuristics() {
        // Multiple OLD segments should be detected as a group
        String input = "MHD=1+ORDERS:9'CLO=123'OLD=1+PROD1'ODD=Desc1'OLD=2+PROD2'ODD=Desc2'MTR=6'";
        
        Message message = parser.parseMessage(input);
        
        // With heuristics, OLD segments should be grouped
        List<Group> groups = message.getGroups("OLD");
        assertEquals(2, groups.size());
        
        // First group should have OLD and ODD
        Group group1 = groups.get(0);
        assertEquals(0, group1.getLoopIndex());
        assertEquals(2, group1.getContent().size());
        
        // Second group should have OLD and ODD
        Group group2 = groups.get(1);
        assertEquals(1, group2.getLoopIndex());
        assertEquals(2, group2.getContent().size());
    }

    @Test
    void testMessageIndexAssignment() {
        String input = 
            "STX=ANAA:1+SENDER+RECEIVER+260115:120000+REF001'" +
            "MHD=1+CREDIT:9'CLO=123'MTR=2'" +
            "MHD=2+ORDERS:9'CLO=456'MTR=2'" +
            "MHD=3+INVOIC:9'CLO=789'MTR=2'" +
            "END=3'";
        
        Batch batch = parser.parseBatch(input);
        
        assertEquals(3, batch.messageCount());
        assertEquals(0, batch.getMessages().get(0).getMessageIndexInBatch());
        assertEquals(1, batch.getMessages().get(1).getMessageIndexInBatch());
        assertEquals(2, batch.getMessages().get(2).getMessageIndexInBatch());
    }

    @Test
    void testEnvelopeMetadataExtraction() {
        String input = 
            "STX=ANAA:1+5012345678901:ACME CORP+5098765432109:BUYER LTD+260115:143052+TRANS001'" +
            "MHD=1+CREDIT:9'CLO=123'MTR=2'" +
            "END=1'";
        
        Batch batch = parser.parseBatch(input);
        
        assertEquals("5012345678901", batch.getSenderId());
        assertEquals("5098765432109", batch.getReceiverId());
        assertEquals("TRANS001", batch.getBatchId());
        
        Instant timestamp = batch.getCreationTimestamp();
        assertNotNull(timestamp);
    }

    @Test
    void testGroupDetectionWithSchema() throws IOException {
        // Load the CREDIT schema
        SchemaLoader loader = new SchemaLoader();
        List<MessageSchema> schemas = loader.loadFromJson(Paths.get("CREDIT.json"));
        assertFalse(schemas.isEmpty());
        
        MessageSchema creditSchema = schemas.get(0);
        Map<String, MessageSchema> registry = Map.of("CREDIT", creditSchema);
        
        EdiParser schemaParser = new EdiParser(ParserConfig.defaults(), registry);
        
        // Create a CREDIT message with CLD groups
        String input = 
            "MHD=1+CREDIT:9'" +
            "CLO=123456+Customer'" +
            "CRF=CN001+260115+260115'" +
            "CLD=1+::PROD1++++10+1000+1000+S+17.5+01'" +
            "CLD=2+::PROD2++++5+500+500+S+17.5+02'" +
            "MTR=6'";
        
        Message message = schemaParser.parseMessage(input);
        
        assertEquals("CREDIT", message.getMessageType());
        
        // Should have CLD groups
        List<Group> cldGroups = message.getGroups("CLD");
        assertEquals(2, cldGroups.size());
        
        // Verify loop indices
        assertEquals(0, cldGroups.get(0).getLoopIndex());
        assertEquals(1, cldGroups.get(1).getLoopIndex());
        
        // Verify trigger segments are marked
        Segment trigger1 = (Segment) cldGroups.get(0).getContent().get(0);
        assertTrue(trigger1.isGroupTrigger());
    }

    @Test
    void testNestedGroupStructure() {
        // Test that nested groups are properly detected with heuristics
        String input = 
            "MHD=1+ORDERS:9'" +
            "CLO=123'" +
            "OLD=1+PROD1'" +
            "ODD=Line 1 Desc'" +
            "DNB=Note1'" +
            "OLD=2+PROD2'" +
            "ODD=Line 2 Desc'" +
            "DNB=Note2'" +
            "MTR=8'";
        
        Message message = parser.parseMessage(input);
        
        // OLD should be detected as groups
        List<Group> oldGroups = message.getGroups("OLD");
        assertEquals(2, oldGroups.size());
        
        // Each OLD group should contain ODD and DNB segments
        Group group1 = oldGroups.get(0);
        assertEquals(3, group1.getContent().size()); // OLD, ODD, DNB
        
        Group group2 = oldGroups.get(1);
        assertEquals(3, group2.getContent().size()); // OLD, ODD, DNB
    }

    @Test
    void testGroupLoopIndexSequential() {
        String input = 
            "MHD=1+ORDERS:9'" +
            "CLO=123'" +
            "OLD=1+PROD1'" +
            "OLD=2+PROD2'" +
            "OLD=3+PROD3'" +
            "OLD=4+PROD4'" +
            "OLD=5+PROD5'" +
            "MTR=7'";
        
        Message message = parser.parseMessage(input);
        
        List<Group> groups = message.getGroups("OLD");
        assertEquals(5, groups.size());
        
        // Verify sequential loop indices starting from 0
        for (int i = 0; i < groups.size(); i++) {
            assertEquals(i, groups.get(i).getLoopIndex(), 
                    "Loop index should be " + i + " for group " + i);
        }
    }
}