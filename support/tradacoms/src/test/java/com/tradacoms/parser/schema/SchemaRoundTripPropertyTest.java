package com.tradacoms.parser.schema;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for schema round-trip consistency.
 * 
 * **Feature: tradacoms-parser, Property 15: Group Round-Trip Consistency**
 * **Validates: Requirements 8.3, 8.4**
 */
class SchemaRoundTripPropertyTest {

    private final SchemaLoader loader = new SchemaLoader();
    private final SchemaSerializer serializer = new SchemaSerializer();

    /**
     * Property 15: Group Round-Trip Consistency (schema portion)
     * 
     * *For any* valid MessageSchema, serializing then deserializing should produce
     * an equivalent schema with identical structure.
     */
    @Property(tries = 100)
    void schemaRoundTripPreservesStructure(
            @ForAll("validMessageSchemas") MessageSchema original
    ) throws IOException {
        // Serialize to JSON
        String json = serializer.writeToJsonString(List.of(original));
        
        // Deserialize back
        List<MessageSchema> reloaded = loader.loadFromJson(json);
        
        // Verify round-trip
        assertEquals(1, reloaded.size(), "Should have exactly one schema");
        MessageSchema roundTripped = reloaded.get(0);
        
        assertEquals(original.getId(), roundTripped.getId());
        assertEquals(original.getMessageClass(), roundTripped.getMessageClass());
        assertEquals(original.getName(), roundTripped.getName());
        assertSegmentsEqual(original.getSegments(), roundTripped.getSegments());
    }


    private void assertSegmentsEqual(
            Map<String, SegmentOrGroupSchema> expected,
            Map<String, SegmentOrGroupSchema> actual
    ) {
        assertEquals(expected.size(), actual.size(), "Segment count mismatch");
        for (String key : expected.keySet()) {
            assertTrue(actual.containsKey(key), "Missing segment: " + key);
            assertSegmentOrGroupEqual(expected.get(key), actual.get(key));
        }
    }

    private void assertSegmentOrGroupEqual(SegmentOrGroupSchema expected, SegmentOrGroupSchema actual) {
        assertEquals(expected.getClass(), actual.getClass());
        if (expected instanceof SegmentSchema expectedSeg) {
            SegmentSchema actualSeg = (SegmentSchema) actual;
            assertEquals(expectedSeg.getId(), actualSeg.getId());
            assertEquals(expectedSeg.getName(), actualSeg.getName());
            assertEquals(expectedSeg.getUsage(), actualSeg.getUsage());
            assertEquals(expectedSeg.getPosition(), actualSeg.getPosition());
            assertEquals(expectedSeg.getCount(), actualSeg.getCount());
            assertElementsEqual(expectedSeg.getValues(), actualSeg.getValues());
        } else if (expected instanceof GroupSchema expectedGroup) {
            GroupSchema actualGroup = (GroupSchema) actual;
            assertEquals(expectedGroup.getGroupId(), actualGroup.getGroupId());
            assertEquals(expectedGroup.getName(), actualGroup.getName());
            assertEquals(expectedGroup.getUsage(), actualGroup.getUsage());
            assertEquals(expectedGroup.getCount(), actualGroup.getCount());
            assertSegmentsEqual(expectedGroup.getSegments(), actualGroup.getSegments());
        }
    }

    private void assertElementsEqual(
            Map<String, ElementSchema> expected,
            Map<String, ElementSchema> actual
    ) {
        assertEquals(expected.size(), actual.size(), "Element count mismatch");
        for (String key : expected.keySet()) {
            assertTrue(actual.containsKey(key), "Missing element: " + key);
            ElementSchema expectedEl = expected.get(key);
            ElementSchema actualEl = actual.get(key);
            assertEquals(expectedEl.getId(), actualEl.getId());
            assertEquals(expectedEl.getName(), actualEl.getName());
            assertEquals(expectedEl.getUsage(), actualEl.getUsage());
            assertEquals(expectedEl.getType(), actualEl.getType());
            assertEquals(expectedEl.getLength(), actualEl.getLength());
            assertEquals(expectedEl.getMinLength(), actualEl.getMinLength());
            assertEquals(expectedEl.getMaxLength(), actualEl.getMaxLength());
            assertComponentsEqual(expectedEl.getValues(), actualEl.getValues());
        }
    }


    private void assertComponentsEqual(List<ComponentSchema> expected, List<ComponentSchema> actual) {
        assertEquals(expected.size(), actual.size(), "Component count mismatch");
        for (int i = 0; i < expected.size(); i++) {
            ComponentSchema expectedComp = expected.get(i);
            ComponentSchema actualComp = actual.get(i);
            assertEquals(expectedComp.getName(), actualComp.getName());
            assertEquals(expectedComp.getUsage(), actualComp.getUsage());
            assertEquals(expectedComp.getType(), actualComp.getType());
            assertEquals(expectedComp.getLength(), actualComp.getLength());
            assertEquals(expectedComp.getMinLength(), actualComp.getMinLength());
            assertEquals(expectedComp.getMaxLength(), actualComp.getMaxLength());
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<MessageSchema> validMessageSchemas() {
        return Combinators.combine(
                messageIds(),
                messageClasses(),
                messageNames(),
                segmentMaps()
        ).as(MessageSchema::new);
    }

    private Arbitrary<String> messageIds() {
        return Arbitraries.of("ORDERS", "INVOIC", "CREDIT", "DELIVR", "PRICAT", "ACKHDR");
    }

    private Arbitrary<String> messageClasses() {
        return Arbitraries.of("1", "2", "3", null);
    }

    private Arbitrary<String> messageNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> s + ":9");
    }


    private Arbitrary<Map<String, SegmentOrGroupSchema>> segmentMaps() {
        return segmentSchemas()
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .map(segments -> {
                    Map<String, SegmentOrGroupSchema> map = new LinkedHashMap<>();
                    for (SegmentSchema seg : segments) {
                        map.put(seg.getId(), seg);
                    }
                    return map;
                });
    }

    private Arbitrary<SegmentSchema> segmentSchemas() {
        return Combinators.combine(
                segmentIds(),
                segmentNames(),
                slugs(),
                positions(),
                usages(),
                counts(),
                elementMaps()
        ).as(SegmentSchema::new);
    }

    private Arbitrary<String> segmentIds() {
        return Arbitraries.of("MHD", "CLO", "CRF", "OLD", "ODD", "MTR", "DNA", "PYT");
    }

    private Arbitrary<String> segmentNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(30)
                .map(String::toUpperCase);
    }

    private Arbitrary<String> slugs() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(30)
                .map(String::toLowerCase)
                .map(s -> s + "_slug");
    }

    private Arbitrary<String> positions() {
        return Arbitraries.integers()
                .between(1, 20)
                .map(i -> String.format("%02d", i));
    }

    private Arbitrary<String> usages() {
        return Arbitraries.of("M", "O");
    }

    private Arbitrary<String> counts() {
        return Arbitraries.of(null, ">1", "1", "2", "5");
    }


    private Arbitrary<Map<String, ElementSchema>> elementMaps() {
        return elementSchemas()
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .map(elements -> {
                    Map<String, ElementSchema> map = new LinkedHashMap<>();
                    for (ElementSchema el : elements) {
                        map.put(el.getId(), el);
                    }
                    return map;
                });
    }

    private Arbitrary<ElementSchema> elementSchemas() {
        // Split into two combines since jqwik only supports up to 8 parameters
        return Combinators.combine(
                elementIds(),
                elementNames(),
                slugs(),
                usages(),
                types(),
                lengthTuples(),
                componentLists()
        ).as((id, name, slug, usage, type, lengths, components) ->
                new ElementSchema(id, name, slug, usage, type, 
                        lengths[0], lengths[1], lengths[2], components));
    }

    private Arbitrary<Integer[]> lengthTuples() {
        return Combinators.combine(lengths(), minLengths(), maxLengths())
                .as((l, min, max) -> new Integer[]{l, min, max});
    }

    private Arbitrary<String> elementIds() {
        return Arbitraries.strings()
                .alpha()
                .ofLength(4)
                .map(String::toUpperCase);
    }

    private Arbitrary<String> elementNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(30);
    }

    private Arbitrary<String> types() {
        return Arbitraries.of("int", "char", "num.3", "num.4", null);
    }

    private Arbitrary<Integer> lengths() {
        return Arbitraries.of(null, 1, 6, 13, 17);
    }

    private Arbitrary<Integer> minLengths() {
        return Arbitraries.of(null, 1, 3, 5);
    }

    private Arbitrary<Integer> maxLengths() {
        return Arbitraries.of(null, 10, 17, 40);
    }


    private Arbitrary<List<ComponentSchema>> componentLists() {
        return Arbitraries.oneOf(
                Arbitraries.just(List.of()),
                componentSchemas().list().ofMinSize(1).ofMaxSize(3)
        );
    }

    private Arbitrary<ComponentSchema> componentSchemas() {
        return Combinators.combine(
                componentNames(),
                slugs(),
                usages(),
                types(),
                lengths(),
                minLengths(),
                maxLengths()
        ).as(ComponentSchema::new);
    }

    private Arbitrary<String> componentNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(30);
    }

    /**
     * Property test for nested group round-trip.
     * Verifies that schemas with nested groups serialize and deserialize correctly.
     */
    @Property(tries = 100)
    void nestedGroupRoundTripPreservesStructure(
            @ForAll("schemasWithGroups") MessageSchema original
    ) throws IOException {
        String json = serializer.writeToJsonString(List.of(original));
        List<MessageSchema> reloaded = loader.loadFromJson(json);
        
        assertEquals(1, reloaded.size());
        MessageSchema roundTripped = reloaded.get(0);
        
        assertEquals(original.getId(), roundTripped.getId());
        assertSegmentsEqual(original.getSegments(), roundTripped.getSegments());
    }

    @Provide
    Arbitrary<MessageSchema> schemasWithGroups() {
        return Combinators.combine(
                messageIds(),
                messageClasses(),
                messageNames(),
                segmentMapsWithGroups()
        ).as(MessageSchema::new);
    }


    private Arbitrary<Map<String, SegmentOrGroupSchema>> segmentMapsWithGroups() {
        return Combinators.combine(
                segmentSchemas().list().ofMinSize(1).ofMaxSize(3),
                groupSchemas().list().ofMinSize(1).ofMaxSize(2)
        ).as((segments, groups) -> {
            Map<String, SegmentOrGroupSchema> map = new LinkedHashMap<>();
            for (SegmentSchema seg : segments) {
                map.put(seg.getId(), seg);
            }
            for (GroupSchema group : groups) {
                map.put(group.getGroupId(), group);
            }
            return map;
        });
    }

    private Arbitrary<GroupSchema> groupSchemas() {
        return Combinators.combine(
                groupIds(),
                segmentNames(),
                slugs(),
                usages(),
                counts(),
                nestedSegmentMaps()
        ).as(GroupSchema::new);
    }

    private Arbitrary<String> groupIds() {
        return Arbitraries.of("CLD", "OLD", "DNA", "ODD", "PYT");
    }

    private Arbitrary<Map<String, SegmentOrGroupSchema>> nestedSegmentMaps() {
        return segmentSchemas()
                .list()
                .ofMinSize(1)
                .ofMaxSize(3)
                .map(segments -> {
                    Map<String, SegmentOrGroupSchema> map = new LinkedHashMap<>();
                    for (SegmentSchema seg : segments) {
                        map.put(seg.getId(), seg);
                    }
                    return map;
                });
    }
}
