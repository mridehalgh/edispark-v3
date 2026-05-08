package com.tradacoms.parser.model;

import com.tradacoms.parser.TradacomsSyntax;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for core model classes.
 */
class ModelTest {

    @Test
    void elementSingleComponent() {
        Element element = Element.of("TEST");
        assertEquals("TEST", element.getValue());
        assertEquals("TEST", element.getComponent(0));
        assertEquals("", element.getComponent(1));
        assertEquals(1, element.componentCount());
        assertFalse(element.isComposite());
        assertFalse(element.isRepeating());
    }

    @Test
    void elementMultipleComponents() {
        Element element = Element.of(List.of("A", "B", "C"));
        assertEquals("A", element.getValue());
        assertEquals("A", element.getComponent(0));
        assertEquals("B", element.getComponent(1));
        assertEquals("C", element.getComponent(2));
        assertEquals("", element.getComponent(3));
        assertEquals(3, element.componentCount());
        assertTrue(element.isComposite());
    }

    @Test
    void segmentBasicOperations() {
        List<Element> elements = List.of(
                Element.of("VALUE1"),
                Element.of(List.of("A", "B"))
        );
        Segment segment = Segment.of("TST", elements);

        assertEquals("TST", segment.tag());
        assertEquals(2, segment.elementCount());
        assertEquals("VALUE1", segment.getElementValue(0));
        assertEquals("A", segment.getElementValue(1));
        assertTrue(segment.getElement(0).isPresent());
        assertTrue(segment.getElement(1).isPresent());
        assertFalse(segment.getElement(2).isPresent());
        assertFalse(segment.isGroupTrigger());
    }

    @Test
    void groupBasicOperations() {
        Segment seg1 = Segment.of("OLD", List.of(Element.of("LINE1")));
        Segment seg2 = Segment.of("ODD", List.of(Element.of("DESC1")));
        Group group = Group.of("OLD", 0, List.of(seg1, seg2));

        assertEquals("OLD", group.getGroupId());
        assertEquals(0, group.getLoopIndex());
        assertEquals(-1, group.getMaxOccurs());
        assertEquals(2, group.getSegments().size());
        assertEquals(1, group.getSegments("OLD").size());
        assertEquals(1, group.getSegments("ODD").size());
        assertTrue(group.getFirstSegment("OLD").isPresent());
        assertTrue(group.getAllSubgroups().isEmpty());
    }

    @Test
    void groupWithSubgroups() {
        Segment innerSeg = Segment.of("INN", List.of(Element.of("INNER")));
        Group subgroup = Group.of("SUB", 0, List.of(innerSeg));
        Segment outerSeg = Segment.of("OUT", List.of(Element.of("OUTER")));
        Group group = Group.of("MAIN", 0, List.of(outerSeg, subgroup));

        assertEquals(1, group.getSegments().size());
        assertEquals(1, group.getAllSubgroups().size());
        assertEquals(1, group.getSubgroups("SUB").size());
        assertTrue(group.getSubgroups("NONEXISTENT").isEmpty());
    }

    @Test
    void messageBasicOperations() {
        Segment seg1 = Segment.of("CLO", List.of(Element.of("CUST1")));
        Segment seg2 = Segment.of("OLD", List.of(Element.of("LINE1")));
        Message message = Message.of("ORDERS", List.of(seg1, seg2));

        assertEquals("ORDERS", message.getMessageType());
        assertEquals(0, message.getMessageIndexInBatch());
        assertEquals(2, message.getAllSegments().size());
        assertTrue(message.getAllGroups().isEmpty());
    }

    @Test
    void messageWithGroups() {
        Segment clo = Segment.of("CLO", List.of(Element.of("CUST1")));
        Segment old1 = Segment.of("OLD", List.of(Element.of("LINE1")));
        Segment old2 = Segment.of("OLD", List.of(Element.of("LINE2")));
        Group group1 = Group.of("OLD", 0, List.of(old1));
        Group group2 = Group.of("OLD", 1, List.of(old2));
        Message message = Message.of("ORDERS", List.of(clo, group1, group2));

        assertEquals(2, message.getGroups("OLD").size());
        assertTrue(message.getFirstGroup("OLD").isPresent());
        assertEquals(0, message.getFirstGroup("OLD").get().getLoopIndex());
        assertEquals(3, message.getAllSegments().size());
    }

    @Test
    void batchBasicOperations() {
        Message msg1 = Message.of("ORDERS", List.of());
        Message msg2 = Message.of("INVOIC", List.of());
        Batch batch = Batch.of("SENDER1", "RECEIVER1", List.of(msg1, msg2));

        assertEquals("SENDER1", batch.getSenderId());
        assertEquals("RECEIVER1", batch.getReceiverId());
        assertEquals(2, batch.messageCount());
        assertTrue(batch.getMessage(0).isPresent());
        assertTrue(batch.getMessage(1).isPresent());
        assertFalse(batch.getMessage(2).isPresent());
        assertEquals(1, batch.getMessagesByType("ORDERS").size());
        assertEquals(1, batch.getMessagesByType("INVOIC").size());
    }

    @Test
    void tradacomsSyntaxConstants() {
        assertEquals('\'', TradacomsSyntax.SEGMENT_TERMINATOR);
        assertEquals('+', TradacomsSyntax.ELEMENT_SEPARATOR);
        assertEquals(':', TradacomsSyntax.COMPONENT_SEPARATOR);
        assertEquals('=', TradacomsSyntax.TAG_SEPARATOR);
        assertEquals('?', TradacomsSyntax.RELEASE_CHARACTER);

        assertEquals("STX", TradacomsSyntax.STX);
        assertEquals("END", TradacomsSyntax.END);
        assertEquals("MHD", TradacomsSyntax.MHD);
        assertEquals("MTR", TradacomsSyntax.MTR);
    }

    @Test
    void tradacomsSyntaxUtilityMethods() {
        assertTrue(TradacomsSyntax.isSpecialCharacter('\''));
        assertTrue(TradacomsSyntax.isSpecialCharacter('+'));
        assertTrue(TradacomsSyntax.isSpecialCharacter(':'));
        assertTrue(TradacomsSyntax.isSpecialCharacter('='));
        assertTrue(TradacomsSyntax.isSpecialCharacter('?'));
        assertFalse(TradacomsSyntax.isSpecialCharacter('A'));

        assertTrue(TradacomsSyntax.isEnvelopeSegment("STX"));
        assertTrue(TradacomsSyntax.isEnvelopeSegment("END"));
        assertFalse(TradacomsSyntax.isEnvelopeSegment("MHD"));

        assertTrue(TradacomsSyntax.isMessageBoundarySegment("MHD"));
        assertTrue(TradacomsSyntax.isMessageBoundarySegment("MTR"));
        assertFalse(TradacomsSyntax.isMessageBoundarySegment("STX"));
    }

    @Test
    void sourceInfoCreation() {
        SourceInfo info1 = SourceInfo.of("test.edi");
        assertEquals("test.edi", info1.filename());
        assertEquals(0, info1.startOffset());
        assertNull(info1.correlationId());

        SourceInfo info2 = SourceInfo.of("test.edi", "corr-123");
        assertEquals("test.edi", info2.filename());
        assertEquals("corr-123", info2.correlationId());
    }
}
