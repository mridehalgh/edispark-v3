package com.tradacoms.parser.streaming;

import com.tradacoms.parser.ParseException;
import com.tradacoms.parser.ParserConfig;
import com.tradacoms.parser.TradacomsSyntax;
import com.tradacoms.parser.lexer.Lexer;
import com.tradacoms.parser.lexer.LexerConfig;
import com.tradacoms.parser.lexer.Token;
import com.tradacoms.parser.model.Element;
import com.tradacoms.parser.model.Segment;
import com.tradacoms.parser.schema.GroupSchema;
import com.tradacoms.parser.schema.MessageSchema;
import com.tradacoms.parser.schema.SegmentOrGroupSchema;
import com.tradacoms.parser.schema.SegmentSchema;

import java.io.InputStream;
import java.util.*;

/**
 * Event-based streaming reader for TRADACOMS batches.
 * Emits BatchEvent objects as segments are parsed, allowing consumers
 * to process data incrementally without loading the entire batch into memory.
 * 
 * <p>Usage example:
 * <pre>{@code
 * try (BatchEventReader reader = new BatchEventReader(inputStream, config, schemaRegistry)) {
 *     while (reader.hasNext()) {
 *         BatchEvent event = reader.next();
 *         // Process event...
 *     }
 * }
 * }</pre>
 */
public final class BatchEventReader implements AutoCloseable {

    private final Iterator<Token> tokens;
    private final Lexer lexer;
    private final ParserConfig config;
    private final Map<String, MessageSchema> schemaRegistry;
    
    private final Deque<BatchEvent> eventQueue;
    private final Deque<GroupContext> groupStack;
    
    private boolean finished = false;
    private boolean batchStarted = false;
    private boolean batchEnded = false;
    private int currentMessageIndex = -1;
    private String currentMessageType = null;
    private MessageSchema currentSchema = null;
    private Map<String, Integer> groupLoopIndices = new HashMap<>();
    
    // Known TRADACOMS group trigger segment tags
    private static final Set<String> KNOWN_GROUP_TRIGGERS = Set.of(
            "OLD", "CLD", "ILD", "DLD", "DNA", "PYT", "ALD", "PCD", "LIN"
    );

    /**
     * Creates a new BatchEventReader.
     *
     * @param input the input stream containing TRADACOMS batch data
     * @param config parser configuration
     * @param schemaRegistry map of message type to schema (may be empty)
     */
    public BatchEventReader(InputStream input, ParserConfig config, Map<String, MessageSchema> schemaRegistry) {
        this.config = config != null ? config : ParserConfig.defaults();
        this.schemaRegistry = schemaRegistry != null ? Map.copyOf(schemaRegistry) : Map.of();
        this.eventQueue = new ArrayDeque<>();
        this.groupStack = new ArrayDeque<>();
        
        LexerConfig lexerConfig = LexerConfig.builder()
                .charset(this.config.getCharset())
                .segmentTerminator(this.config.getSegmentTerminator())
                .build();
        
        this.lexer = new Lexer(lexerConfig);
        this.tokens = lexer.tokenize(input);
    }

    /**
     * Creates a new BatchEventReader with default configuration.
     */
    public BatchEventReader(InputStream input) {
        this(input, ParserConfig.defaults(), Map.of());
    }

    /**
     * Returns true if there are more events to read.
     */
    public boolean hasNext() {
        if (!eventQueue.isEmpty()) {
            return true;
        }
        if (finished) {
            return false;
        }
        fillEventQueue();
        return !eventQueue.isEmpty();
    }

    /**
     * Returns the next event.
     * @throws NoSuchElementException if no more events are available
     */
    public BatchEvent next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more events");
        }
        return eventQueue.poll();
    }

    @Override
    public void close() {
        // Clean up resources if needed
        finished = true;
        eventQueue.clear();
        groupStack.clear();
    }

    // ========== Internal Methods ==========

    private void fillEventQueue() {
        while (eventQueue.isEmpty() && !finished && tokens.hasNext()) {
            Segment segment = parseNextSegment();
            if (segment == null) {
                continue;
            }
            processSegment(segment);
        }
    }

    private void processSegment(Segment segment) {
        String tag = segment.tag();

        // Handle STX (batch start)
        if (TradacomsSyntax.STX.equals(tag)) {
            handleBatchStart(segment);
            return;
        }

        // Handle END (batch end)
        if (TradacomsSyntax.END.equals(tag)) {
            handleBatchEnd(segment);
            return;
        }

        // Handle MHD (message start)
        if (TradacomsSyntax.MHD.equals(tag)) {
            handleMessageStart(segment);
            return;
        }

        // Handle MTR (message end)
        if (TradacomsSyntax.MTR.equals(tag)) {
            handleMessageEnd(segment);
            return;
        }

        // Regular segment - check for group boundaries
        handleContentSegment(segment);
    }

    private void handleBatchStart(Segment stxSegment) {
        if (batchStarted) {
            throw ParseException.builder("Unexpected STX segment - batch already started")
                    .errorCode("PARSE_002")
                    .segmentTag("STX")
                    .lineNumber(stxSegment.lineNumber())
                    .charPosition(stxSegment.charPosition())
                    .build();
        }
        
        batchStarted = true;
        String batchId = extractBatchId(stxSegment);
        eventQueue.add(new BatchEvent.StartBatch(batchId, stxSegment));
    }

    private void handleBatchEnd(Segment endSegment) {
        // Close any open groups
        closeAllGroups();
        
        batchEnded = true;
        finished = true;
        eventQueue.add(new BatchEvent.EndBatch(endSegment));
    }

    private void handleMessageStart(Segment mhdSegment) {
        // Close any open groups from previous message
        closeAllGroups();
        
        currentMessageIndex++;
        currentMessageType = extractMessageType(mhdSegment);
        currentSchema = schemaRegistry.get(currentMessageType);
        groupLoopIndices.clear();
        
        eventQueue.add(new BatchEvent.StartMessage(currentMessageIndex, currentMessageType));
        
        // Emit the MHD segment itself
        eventQueue.add(new BatchEvent.SegmentRead(mhdSegment));
    }

    private void handleMessageEnd(Segment mtrSegment) {
        // Close any open groups
        closeAllGroups();
        
        // Emit the MTR segment
        eventQueue.add(new BatchEvent.SegmentRead(mtrSegment));
        
        eventQueue.add(new BatchEvent.EndMessage(currentMessageIndex, mtrSegment));
        
        // Reset message state
        currentMessageType = null;
        currentSchema = null;
    }

    private void handleContentSegment(Segment segment) {
        String tag = segment.tag();
        
        // Check if this segment triggers a new group
        if (isGroupTrigger(tag)) {
            // Close any existing group of the same type (new occurrence)
            closeGroupsUntil(tag);
            
            // Start new group
            int loopIndex = groupLoopIndices.getOrDefault(tag, 0);
            groupLoopIndices.put(tag, loopIndex + 1);
            
            groupStack.push(new GroupContext(tag, loopIndex));
            eventQueue.add(new BatchEvent.StartGroup(tag, loopIndex));
            
            // Mark segment as group trigger and emit
            Segment triggerSegment = new Segment(
                    segment.tag(),
                    segment.elements(),
                    segment.lineNumber(),
                    segment.charPosition(),
                    segment.rawContent(),
                    true
            );
            eventQueue.add(new BatchEvent.SegmentRead(triggerSegment));
        } else {
            // Check if this segment belongs to current group or ends it
            if (!groupStack.isEmpty()) {
                GroupContext currentGroup = groupStack.peek();
                if (!belongsToGroup(tag, currentGroup.groupId)) {
                    // Segment doesn't belong to current group - close it
                    closeCurrentGroup();
                }
            }
            
            // Emit the segment
            eventQueue.add(new BatchEvent.SegmentRead(segment));
        }
    }

    private boolean isGroupTrigger(String tag) {
        // First check schema if available
        if (currentSchema != null) {
            GroupSchema groupSchema = findGroupSchemaForTrigger(currentSchema, tag);
            if (groupSchema != null) {
                return true;
            }
        }
        
        // Fall back to known triggers
        return KNOWN_GROUP_TRIGGERS.contains(tag);
    }

    private boolean belongsToGroup(String segmentTag, String groupId) {
        // If we have a schema, use it to determine group membership
        if (currentSchema != null) {
            GroupSchema groupSchema = findGroupSchemaById(currentSchema, groupId);
            if (groupSchema != null) {
                Set<String> groupSegmentIds = collectGroupSegmentIds(groupSchema);
                return groupSegmentIds.contains(segmentTag);
            }
        }
        
        // Without schema, assume non-trigger segments belong to current group
        return !KNOWN_GROUP_TRIGGERS.contains(segmentTag);
    }

    private void closeCurrentGroup() {
        if (!groupStack.isEmpty()) {
            GroupContext context = groupStack.pop();
            eventQueue.add(new BatchEvent.EndGroup(context.groupId, context.loopIndex));
        }
    }

    private void closeGroupsUntil(String triggerTag) {
        // Close groups until we find one with the same trigger tag or stack is empty
        while (!groupStack.isEmpty()) {
            GroupContext context = groupStack.peek();
            if (context.groupId.equals(triggerTag)) {
                // Close this group - new occurrence of same type
                closeCurrentGroup();
                break;
            }
            closeCurrentGroup();
        }
    }

    private void closeAllGroups() {
        while (!groupStack.isEmpty()) {
            closeCurrentGroup();
        }
    }

    // ========== Schema Helpers ==========

    private GroupSchema findGroupSchemaForTrigger(MessageSchema schema, String segmentTag) {
        for (SegmentOrGroupSchema s : schema.getSegments().values()) {
            if (s instanceof GroupSchema gs) {
                List<String> order = gs.getSegmentOrder();
                if (!order.isEmpty()) {
                    String firstKey = order.get(0);
                    SegmentOrGroupSchema first = gs.getSegments().get(firstKey);
                    if (first instanceof SegmentSchema ss && segmentTag.equals(ss.getId())) {
                        return gs;
                    }
                }
                if (segmentTag.equals(gs.getGroupId())) {
                    return gs;
                }
            }
        }
        return null;
    }

    private GroupSchema findGroupSchemaById(MessageSchema schema, String groupId) {
        for (SegmentOrGroupSchema s : schema.getSegments().values()) {
            if (s instanceof GroupSchema gs && groupId.equals(gs.getGroupId())) {
                return gs;
            }
        }
        return null;
    }

    private Set<String> collectGroupSegmentIds(GroupSchema groupSchema) {
        Set<String> ids = new HashSet<>();
        for (SegmentOrGroupSchema s : groupSchema.getSegments().values()) {
            if (s instanceof SegmentSchema ss) {
                ids.add(ss.getId());
            } else if (s instanceof GroupSchema gs) {
                ids.add(gs.getGroupId());
                ids.addAll(collectGroupSegmentIds(gs));
            }
        }
        return ids;
    }

    // ========== Segment Parsing ==========

    private Segment parseNextSegment() {
        String tag = null;
        int lineNumber = 0;
        int charPosition = 0;
        List<Element> elements = new ArrayList<>();
        List<String> currentComponents = new ArrayList<>();
        StringBuilder rawContent = new StringBuilder();
        int lastComponentIndex = -1;
        
        while (tokens.hasNext()) {
            Token token = tokens.next();
            
            if (token instanceof Token.SegmentStart start) {
                tag = start.tag();
                lineNumber = start.line();
                charPosition = start.position();
                rawContent.append(tag).append(TradacomsSyntax.TAG_SEPARATOR);
                elements.clear();
                currentComponents.clear();
                lastComponentIndex = -1;
            } else if (token instanceof Token.ElementValue ev) {
                if (!currentComponents.isEmpty()) {
                    elements.add(Element.of(new ArrayList<>(currentComponents)));
                    currentComponents.clear();
                    lastComponentIndex = -1;
                }
                String unescaped = lexer.unescape(ev.value());
                elements.add(Element.of(unescaped));
                if (rawContent.length() > 0 && rawContent.charAt(rawContent.length() - 1) != TradacomsSyntax.TAG_SEPARATOR) {
                    rawContent.append(TradacomsSyntax.ELEMENT_SEPARATOR);
                }
                rawContent.append(ev.value());
            } else if (token instanceof Token.ComponentValue cv) {
                if (cv.index() == 0 && !currentComponents.isEmpty()) {
                    elements.add(Element.of(new ArrayList<>(currentComponents)));
                    currentComponents.clear();
                    if (rawContent.length() > 0 && rawContent.charAt(rawContent.length() - 1) != TradacomsSyntax.TAG_SEPARATOR) {
                        rawContent.append(TradacomsSyntax.ELEMENT_SEPARATOR);
                    }
                } else if (cv.index() > 0 && lastComponentIndex >= 0) {
                    rawContent.append(TradacomsSyntax.COMPONENT_SEPARATOR);
                }
                
                String unescaped = lexer.unescape(cv.value());
                currentComponents.add(unescaped);
                rawContent.append(cv.value());
                lastComponentIndex = cv.index();
            } else if (token instanceof Token.SegmentEnd) {
                if (!currentComponents.isEmpty()) {
                    elements.add(Element.of(new ArrayList<>(currentComponents)));
                }
                
                rawContent.append(TradacomsSyntax.SEGMENT_TERMINATOR);
                
                if (tag != null) {
                    return new Segment(tag, elements, lineNumber, charPosition, rawContent.toString(), false);
                }
            } else if (token instanceof Token.EndOfInput) {
                finished = true;
                return null;
            } else if (token instanceof Token.Error error) {
                throw ParseException.builder(error.message())
                        .errorCode("PARSE_002")
                        .lineNumber(error.line())
                        .charPosition(error.position())
                        .rawSnippet(error.snippet())
                        .build();
            }
        }
        
        return null;
    }

    // ========== Metadata Extraction ==========

    private String extractMessageType(Segment mhdSegment) {
        if (mhdSegment.elements().size() >= 2) {
            Element typeElement = mhdSegment.elements().get(1);
            return typeElement.getValue();
        }
        return "UNKNOWN";
    }

    private String extractBatchId(Segment stxSegment) {
        if (stxSegment.elements().size() >= 5) {
            Element refElement = stxSegment.elements().get(4);
            return refElement.getValue();
        }
        return null;
    }

    // ========== Helper Classes ==========

    private record GroupContext(String groupId, int loopIndex) {}
}
