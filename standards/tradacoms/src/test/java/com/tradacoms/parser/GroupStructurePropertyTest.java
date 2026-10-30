package com.tradacoms.parser;

import com.tradacoms.parser.model.Group;
import com.tradacoms.parser.model.Message;
import com.tradacoms.parser.model.Segment;
import com.tradacoms.parser.model.SegmentOrGroup;
import net.jqwik.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for group structure preservation in TRADACOMS parsing.
 * 
 * **Feature: tradacoms-parser, Property 14: Group Structure Preservation**
 * **Validates: Requirements 1.1, 1.2**
 */
class GroupStructurePropertyTest {

    private final EdiParser parser = new EdiParser();

    /**
     * Property 14: Group Structure Preservation
     * 
     * *For any* message containing groups/loops, the parsed Message SHALL preserve:
     * (a) group boundaries matching trigger segments
     * (b) loop indices assigned sequentially starting from 0
     * (c) all segments within each group in original order
     */
    @Property(tries = 100)
    void groupTriggerSegmentsAreMarked(
            @ForAll("messagesWithRepeatingSegments") GeneratedMessage generated
    ) {
        Message message = parser.parseMessage(generated.tradacomsString());
        
        // Get all groups
        List<Group> groups = message.getGroups(generated.repeatingTag());
        
        // Each group should have its first segment marked as trigger
        for (Group group : groups) {
            assertFalse(group.getContent().isEmpty(),
                    "Group should not be empty");
            
            SegmentOrGroup first = group.getContent().get(0);
            assertTrue(first instanceof Segment,
                    "First item in group should be a segment");
            
            Segment trigger = (Segment) first;
            assertEquals(generated.repeatingTag(), trigger.tag(),
                    "Trigger segment tag should match group ID");
            assertTrue(trigger.isGroupTrigger(),
                    "Trigger segment should be marked as group trigger");
        }
    }

    @Property(tries = 100)
    void loopIndicesAreSequentialFromZero(
            @ForAll("messagesWithRepeatingSegments") GeneratedMessage generated
    ) {
        Message message = parser.parseMessage(generated.tradacomsString());
        
        List<Group> groups = message.getGroups(generated.repeatingTag());
        
        // Verify indices are 0, 1, 2, ...
        for (int i = 0; i < groups.size(); i++) {
            assertEquals(i, groups.get(i).getLoopIndex(),
                    "Group occurrence " + i + " should have loop index " + i);
        }
    }

    @Property(tries = 100)
    void groupCountMatchesRepeatingSegmentCount(
            @ForAll("messagesWithRepeatingSegments") GeneratedMessage generated
    ) {
        Message message = parser.parseMessage(generated.tradacomsString());
        
        List<Group> groups = message.getGroups(generated.repeatingTag());
        
        // Number of groups should match number of repeating segment occurrences
        assertEquals(generated.repeatCount(), groups.size(),
                "Group count should match repeating segment count");
    }

    @Property(tries = 100)
    void segmentsWithinGroupsPreserveOrder(
            @ForAll("messagesWithRepeatingSegments") GeneratedMessage generated
    ) {
        Message message = parser.parseMessage(generated.tradacomsString());
        
        List<Group> groups = message.getGroups(generated.repeatingTag());
        
        // Each group should contain the trigger segment followed by following segments
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            List<String> actualTags = group.getContent().stream()
                    .filter(item -> item instanceof Segment)
                    .map(item -> ((Segment) item).tag())
                    .collect(Collectors.toList());
            
            // First tag should be the trigger
            assertEquals(generated.repeatingTag(), actualTags.get(0),
                    "First segment in group should be trigger");
            
            // Following tags should be the following segments
            List<String> expectedFollowing = generated.followingTags();
            for (int j = 0; j < expectedFollowing.size(); j++) {
                assertEquals(expectedFollowing.get(j), actualTags.get(j + 1),
                        "Following segment " + j + " should match");
            }
        }
    }

    /**
     * Property: All segments from groups are accessible via getAllSegments().
     */
    @Property(tries = 100)
    void allSegmentsAccessibleViaFlattenedView(
            @ForAll("messagesWithRepeatingSegments") GeneratedMessage generated
    ) {
        Message message = parser.parseMessage(generated.tradacomsString());
        
        List<Segment> allSegments = message.getAllSegments();
        
        // Total segment count should match expected
        assertEquals(generated.totalSegmentCount(), allSegments.size(),
                "Total segment count should match");
    }

    /**
     * Property: Groups can be queried by ID.
     */
    @Property(tries = 100)
    void groupsQueryableById(
            @ForAll("messagesWithRepeatingSegments") GeneratedMessage generated
    ) {
        Message message = parser.parseMessage(generated.tradacomsString());
        
        List<Group> groups = message.getGroups(generated.repeatingTag());
        
        assertFalse(groups.isEmpty(),
                "Should find groups with ID " + generated.repeatingTag());
        
        // All returned groups should have the correct ID
        for (Group group : groups) {
            assertEquals(generated.repeatingTag(), group.getGroupId(),
                    "Group ID should match query");
        }
    }

    /**
     * Property: First group is accessible via getFirstGroup().
     */
    @Property(tries = 100)
    void firstGroupAccessible(
            @ForAll("messagesWithRepeatingSegments") GeneratedMessage generated
    ) {
        Message message = parser.parseMessage(generated.tradacomsString());
        
        Optional<Group> firstGroup = message.getFirstGroup(generated.repeatingTag());
        
        assertTrue(firstGroup.isPresent(),
                "First group should be accessible");
        assertEquals(0, firstGroup.get().getLoopIndex(),
                "First group should have loop index 0");
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<GeneratedMessage> messagesWithRepeatingSegments() {
        return Combinators.combine(
                messageTypes(),
                repeatingTags(),
                Arbitraries.integers().between(1, 5),
                followingTagLists()
        ).as((type, repeatingTag, repeatCount, followingTags) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("MHD=1+").append(type).append(":9'");
            sb.append("CLO=123456'"); // Non-repeating segment
            
            int totalSegments = 1; // CLO
            
            for (int i = 0; i < repeatCount; i++) {
                // Add the repeating (trigger) segment
                sb.append(repeatingTag).append("=").append(i + 1).append("+DATA").append(i).append("'");
                totalSegments++;
                
                // Add following segments
                for (String followingTag : followingTags) {
                    sb.append(followingTag).append("=").append(i + 1).append("+INFO").append(i).append("'");
                    totalSegments++;
                }
            }
            
            sb.append("MTR=").append(totalSegments + 2).append("'");
            
            return new GeneratedMessage(
                    sb.toString(),
                    repeatingTag,
                    repeatCount,
                    followingTags,
                    totalSegments
            );
        });
    }

    private Arbitrary<String> messageTypes() {
        return Arbitraries.of("CREDIT", "ORDERS", "INVOIC");
    }

    private Arbitrary<String> repeatingTags() {
        return Arbitraries.of("OLD", "CLD", "DNA", "PYT");
    }

    private Arbitrary<List<String>> followingTagLists() {
        // Generate 0-2 following segment tags that are different from repeating tags
        return Arbitraries.of("ODD", "DNB", "FTX", "QTY", "VAT")
                .list()
                .ofMinSize(0)
                .ofMaxSize(2);
    }

    // ========== Record Types ==========

    record GeneratedMessage(
            String tradacomsString,
            String repeatingTag,
            int repeatCount,
            List<String> followingTags,
            int totalSegmentCount
    ) {}
}
