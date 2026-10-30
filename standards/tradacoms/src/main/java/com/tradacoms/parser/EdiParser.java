package com.tradacoms.parser;

import com.tradacoms.parser.lexer.Lexer;
import com.tradacoms.parser.lexer.LexerConfig;
import com.tradacoms.parser.lexer.Token;
import com.tradacoms.parser.model.*;
import com.tradacoms.parser.schema.GroupSchema;
import com.tradacoms.parser.schema.MessageSchema;
import com.tradacoms.parser.schema.SegmentOrGroupSchema;
import com.tradacoms.parser.streaming.BatchEvent;
import com.tradacoms.parser.streaming.BatchEventReader;
import com.tradacoms.parser.streaming.MessageStreamIterator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parser for TRADACOMS EDI documents.
 * Supports both DOM-style parsing (loading entire structure into memory)
 * and streaming parsing (event-based processing).
 */
public final class EdiParser {

    private final ParserConfig config;
    private final Map<String, MessageSchema> schemaRegistry;

    public EdiParser() {
        this(ParserConfig.defaults(), Map.of());
    }

    public EdiParser(ParserConfig config) {
        this(config, Map.of());
    }

    public EdiParser(ParserConfig config, Map<String, MessageSchema> schemaRegistry) {
        this.config = config != null ? config : ParserConfig.defaults();
        this.schemaRegistry = schemaRegistry != null ? Map.copyOf(schemaRegistry) : Map.of();
    }

    /**
     * Parse a single TRADACOMS message from an input stream.
     * The input should contain segments for a single message (MHD to MTR).
     *
     * @param input the input stream containing TRADACOMS message data
     * @return the parsed Message object
     * @throws ParseException if parsing fails
     */
    public Message parseMessage(InputStream input) {
        return parseMessage(input, config);
    }

    /**
     * Parse a single TRADACOMS message from an input stream with custom config.
     *
     * @param input the input stream containing TRADACOMS message data
     * @param config parser configuration
     * @return the parsed Message object
     * @throws ParseException if parsing fails
     */
    public Message parseMessage(InputStream input, ParserConfig config) {
        LexerConfig lexerConfig = LexerConfig.builder()
                .charset(config.getCharset())
                .segmentTerminator(config.getSegmentTerminator())
                .build();
        
        Lexer lexer = new Lexer(lexerConfig);
        Iterator<Token> tokens = lexer.tokenize(input);
        
        return parseMessageFromTokens(tokens, lexer, 0, null);
    }

    /**
     * Parse a single TRADACOMS message from a string.
     *
     * @param input the string containing TRADACOMS message data
     * @return the parsed Message object
     * @throws ParseException if parsing fails
     */
    public Message parseMessage(String input) {
        return parseMessage(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }


    /**
     * Parse a TRADACOMS batch (interchange) from an input stream.
     * A batch contains STX envelope, one or more messages, and END trailer.
     *
     * @param input the input stream containing TRADACOMS batch data
     * @return the parsed Batch object
     * @throws ParseException if parsing fails
     */
    public Batch parseBatch(InputStream input) {
        return parseBatch(input, config);
    }

    /**
     * Parse a TRADACOMS batch (interchange) from an input stream with custom config.
     *
     * @param input the input stream containing TRADACOMS batch data
     * @param config parser configuration
     * @return the parsed Batch object
     * @throws ParseException if parsing fails
     */
    public Batch parseBatch(InputStream input, ParserConfig config) {
        LexerConfig lexerConfig = LexerConfig.builder()
                .charset(config.getCharset())
                .segmentTerminator(config.getSegmentTerminator())
                .build();
        
        Lexer lexer = new Lexer(lexerConfig);
        Iterator<Token> tokens = lexer.tokenize(input);
        
        return parseBatchFromTokens(tokens, lexer, config);
    }

    /**
     * Parse a TRADACOMS batch from a string.
     *
     * @param input the string containing TRADACOMS batch data
     * @return the parsed Batch object
     * @throws ParseException if parsing fails
     */
    public Batch parseBatch(String input) {
        return parseBatch(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    // ========== Streaming Methods ==========

    /**
     * Stream a TRADACOMS batch as events.
     * Returns a BatchEventReader that emits events as segments are parsed,
     * allowing consumers to process data incrementally without loading the
     * entire batch into memory.
     *
     * @param input the input stream containing TRADACOMS batch data
     * @return a BatchEventReader for event-based processing
     */
    public BatchEventReader streamBatch(InputStream input) {
        return streamBatch(input, config);
    }

    /**
     * Stream a TRADACOMS batch as events with custom configuration.
     *
     * @param input the input stream containing TRADACOMS batch data
     * @param config parser configuration
     * @return a BatchEventReader for event-based processing
     */
    public BatchEventReader streamBatch(InputStream input, ParserConfig config) {
        return new BatchEventReader(input, config, schemaRegistry);
    }

    /**
     * Stream messages from a TRADACOMS batch.
     * Returns an Iterator that buffers one message at a time, emitting complete
     * Message objects. This provides a balance between memory efficiency and
     * ease of use compared to event-based streaming.
     *
     * <p>The iterator holds at most one message in memory at any time.
     *
     * @param input the input stream containing TRADACOMS batch data
     * @return an Iterator of Message objects
     */
    public Iterator<Message> streamMessages(InputStream input) {
        return streamMessages(input, config);
    }

    /**
     * Stream messages from a TRADACOMS batch with custom configuration.
     *
     * @param input the input stream containing TRADACOMS batch data
     * @param config parser configuration
     * @return an Iterator of Message objects
     */
    public Iterator<Message> streamMessages(InputStream input, ParserConfig config) {
        return new MessageStreamIterator(input, config, schemaRegistry);
    }

    // ========== Internal Parsing Methods ==========

    private Batch parseBatchFromTokens(Iterator<Token> tokens, Lexer lexer, ParserConfig config) {
        Segment stxSegment = null;
        Segment endSegment = null;
        List<Message> messages = new ArrayList<>();
        List<ParseException> errors = new ArrayList<>();
        int includedMessageCount = 0;  // Count of messages that pass filtering
        
        // Parse STX envelope first
        Segment firstSegment = parseNextSegment(tokens, lexer);
        if (firstSegment == null) {
            throw new ParseException("Empty input - no segments found");
        }
        
        if (TradacomsSyntax.STX.equals(firstSegment.tag())) {
            stxSegment = firstSegment;
        } else {
            throw ParseException.builder("Expected STX segment at start of batch, found: " + firstSegment.tag())
                    .errorCode("ENV_001")
                    .segmentTag(firstSegment.tag())
                    .lineNumber(firstSegment.lineNumber())
                    .charPosition(firstSegment.charPosition())
                    .rawSnippet(firstSegment.rawContent())
                    .build();
        }
        
        // Extract envelope metadata from STX
        String senderId = extractSenderId(stxSegment);
        String receiverId = extractReceiverId(stxSegment);
        Instant creationTimestamp = extractTimestamp(stxSegment);
        String batchId = extractBatchId(stxSegment);
        
        // Parse messages until END segment
        while (tokens.hasNext()) {
            Segment segment = parseNextSegment(tokens, lexer);
            if (segment == null) {
                break;
            }
            
            if (TradacomsSyntax.END.equals(segment.tag())) {
                endSegment = segment;
                break;
            }
            
            if (TradacomsSyntax.MHD.equals(segment.tag())) {
                // Check max messages limit against included messages count
                if (includedMessageCount >= config.getMaxMessages()) {
                    // Skip remaining messages but continue to find END segment
                    skipToMessageEnd(tokens, lexer);
                    continue;
                }
                
                try {
                    // Parse the message starting from this MHD segment
                    Message message = parseMessageFromMHD(segment, tokens, lexer, includedMessageCount);
                    
                    // Apply message type filtering
                    if (config.shouldIncludeMessageType(message.getMessageType())) {
                        messages.add(message);
                        includedMessageCount++;
                    }
                } catch (ParseException e) {
                    if (config.isStopOnError()) {
                        throw e;
                    }
                    if (config.isContinueOnError()) {
                        errors.add(e);
                        // Continue parsing next message
                    } else {
                        throw e;
                    }
                }
            }
        }
        
        if (endSegment == null) {
            throw ParseException.builder("Missing END segment at end of batch")
                    .errorCode("ENV_002")
                    .build();
        }
        
        return new Batch(
                batchId,
                senderId,
                receiverId,
                creationTimestamp,
                messages,
                stxSegment,
                endSegment,
                null
        );
    }

    /**
     * Skip tokens until the end of the current message (MTR segment).
     * Used when maxMessages limit is reached but we need to continue to find END segment.
     */
    private void skipToMessageEnd(Iterator<Token> tokens, Lexer lexer) {
        while (tokens.hasNext()) {
            Token token = tokens.next();
            if (token instanceof Token.SegmentStart start) {
                if (TradacomsSyntax.MTR.equals(start.tag())) {
                    // Skip to segment end
                    while (tokens.hasNext()) {
                        Token t = tokens.next();
                        if (t instanceof Token.SegmentEnd) {
                            return;
                        }
                    }
                }
            }
        }
    }


    private Message parseMessageFromTokens(Iterator<Token> tokens, Lexer lexer, int messageIndex, MessageSchema schema) {
        Segment firstSegment = parseNextSegment(tokens, lexer);
        if (firstSegment == null) {
            throw new ParseException("Empty input - no segments found");
        }
        
        if (TradacomsSyntax.MHD.equals(firstSegment.tag())) {
            return parseMessageFromMHD(firstSegment, tokens, lexer, messageIndex);
        } else {
            // If not starting with MHD, treat all segments as message content
            List<SegmentOrGroup> content = new ArrayList<>();
            content.add(firstSegment);
            
            while (tokens.hasNext()) {
                Segment segment = parseNextSegment(tokens, lexer);
                if (segment == null) {
                    break;
                }
                content.add(segment);
            }
            
            return new Message("UNKNOWN", messageIndex, null, content, Map.of(), null, null);
        }
    }

    private Message parseMessageFromMHD(Segment mhdSegment, Iterator<Token> tokens, Lexer lexer, int messageIndex) {
        // Extract message type from MHD segment
        String messageType = extractMessageType(mhdSegment);
        String messageControlRef = extractMessageControlRef(mhdSegment);
        
        // Get schema for this message type if available
        MessageSchema schema = schemaRegistry.get(messageType);
        
        List<SegmentOrGroup> content = new ArrayList<>();
        Segment mtrSegment = null;
        List<Segment> rawSegments = new ArrayList<>();
        
        // Parse segments until MTR
        while (tokens.hasNext()) {
            Segment segment = parseNextSegment(tokens, lexer);
            if (segment == null) {
                break;
            }
            
            if (TradacomsSyntax.MTR.equals(segment.tag())) {
                mtrSegment = segment;
                break;
            }
            
            rawSegments.add(segment);
        }
        
        // Process segments into content with group detection
        if (schema != null) {
            content = buildContentWithSchema(rawSegments, schema);
        } else {
            content = buildContentWithHeuristics(rawSegments);
        }
        
        return new Message(
                messageType,
                messageIndex,
                messageControlRef,
                content,
                Map.of(),
                mhdSegment,
                mtrSegment
        );
    }

    /**
     * Build message content using schema-based group detection.
     */
    private List<SegmentOrGroup> buildContentWithSchema(List<Segment> segments, MessageSchema schema) {
        List<SegmentOrGroup> content = new ArrayList<>();
        Map<String, Integer> groupLoopIndices = new HashMap<>();
        
        int i = 0;
        while (i < segments.size()) {
            Segment segment = segments.get(i);
            String tag = segment.tag();
            
            // Check if this segment is a group trigger
            GroupSchema groupSchema = findGroupSchemaForTrigger(schema, tag);
            
            if (groupSchema != null) {
                // Parse the group
                GroupParseResult result = parseGroup(segments, i, groupSchema, groupLoopIndices, schema);
                content.add(result.group);
                i = result.nextIndex;
            } else {
                // Regular segment
                content.add(segment);
                i++;
            }
        }
        
        return content;
    }

    /**
     * Find the group schema that has the given segment tag as its trigger.
     */
    private GroupSchema findGroupSchemaForTrigger(MessageSchema schema, String segmentTag) {
        for (SegmentOrGroupSchema s : schema.getSegments().values()) {
            if (s instanceof GroupSchema gs) {
                // Check if this segment is the first segment in the group (trigger)
                List<String> order = gs.getSegmentOrder();
                if (!order.isEmpty()) {
                    String firstKey = order.get(0);
                    SegmentOrGroupSchema first = gs.getSegments().get(firstKey);
                    if (first instanceof com.tradacoms.parser.schema.SegmentSchema ss && segmentTag.equals(ss.getId())) {
                        return gs;
                    }
                    // Also check if the group ID matches the segment tag (common pattern)
                    if (segmentTag.equals(gs.getGroupId())) {
                        return gs;
                    }
                }
            }
        }
        return null;
    }


    /**
     * Parse a group from the segment list starting at the given index.
     */
    private GroupParseResult parseGroup(
            List<Segment> segments, 
            int startIndex, 
            GroupSchema groupSchema,
            Map<String, Integer> groupLoopIndices,
            MessageSchema messageSchema
    ) {
        String groupId = groupSchema.getGroupId();
        int loopIndex = groupLoopIndices.getOrDefault(groupId, 0);
        groupLoopIndices.put(groupId, loopIndex + 1);
        
        List<SegmentOrGroup> groupContent = new ArrayList<>();
        Set<String> groupSegmentIds = collectGroupSegmentIds(groupSchema);
        Map<String, Integer> nestedGroupIndices = new HashMap<>();
        
        int i = startIndex;
        
        // Add the trigger segment
        Segment triggerSegment = new Segment(
                segments.get(i).tag(),
                segments.get(i).elements(),
                segments.get(i).lineNumber(),
                segments.get(i).charPosition(),
                segments.get(i).rawContent(),
                true  // Mark as group trigger
        );
        groupContent.add(triggerSegment);
        i++;
        
        // Continue parsing until we hit a segment that's not part of this group
        // or we hit another occurrence of the trigger segment
        while (i < segments.size()) {
            Segment segment = segments.get(i);
            String tag = segment.tag();
            
            // Check if this is another occurrence of the same group trigger
            if (tag.equals(groupId) || (groupSchema.getSegmentOrder().size() > 0 && 
                    tag.equals(groupSchema.getSegmentOrder().get(0)))) {
                // End of this group occurrence
                break;
            }
            
            // Check for nested groups
            GroupSchema nestedGroupSchema = findNestedGroupSchema(groupSchema, tag);
            if (nestedGroupSchema != null) {
                GroupParseResult nestedResult = parseGroup(segments, i, nestedGroupSchema, nestedGroupIndices, messageSchema);
                groupContent.add(nestedResult.group);
                i = nestedResult.nextIndex;
                continue;
            }
            
            // Check if segment belongs to this group
            if (groupSegmentIds.contains(tag)) {
                groupContent.add(segment);
                i++;
            } else {
                // Segment doesn't belong to this group - end the group
                break;
            }
        }
        
        Group group = new Group(groupId, loopIndex, groupSchema.getMaxOccurs(), groupContent);
        return new GroupParseResult(group, i);
    }

    /**
     * Find a nested group schema within a parent group.
     */
    private GroupSchema findNestedGroupSchema(GroupSchema parentGroup, String segmentTag) {
        for (SegmentOrGroupSchema s : parentGroup.getSegments().values()) {
            if (s instanceof GroupSchema gs) {
                // Check if this segment triggers the nested group
                List<String> order = gs.getSegmentOrder();
                if (!order.isEmpty()) {
                    String firstKey = order.get(0);
                    SegmentOrGroupSchema first = gs.getSegments().get(firstKey);
                    if (first instanceof com.tradacoms.parser.schema.SegmentSchema ss && segmentTag.equals(ss.getId())) {
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

    /**
     * Collect all segment IDs that belong to a group (including nested groups).
     */
    private Set<String> collectGroupSegmentIds(GroupSchema groupSchema) {
        Set<String> ids = new HashSet<>();
        for (SegmentOrGroupSchema s : groupSchema.getSegments().values()) {
            if (s instanceof com.tradacoms.parser.schema.SegmentSchema ss) {
                ids.add(ss.getId());
            } else if (s instanceof GroupSchema gs) {
                ids.add(gs.getGroupId());
                ids.addAll(collectGroupSegmentIds(gs));
            }
        }
        return ids;
    }

    /**
     * Known TRADACOMS group trigger segment tags.
     * These segments typically start repeating groups/loops in TRADACOMS messages.
     * This is a conservative list of primary group triggers - segments that
     * almost always indicate the start of a repeating group.
     */
    private static final Set<String> KNOWN_GROUP_TRIGGERS = Set.of(
            "OLD",  // Order Line Detail
            "CLD",  // Credit Line Detail
            "ILD",  // Invoice Line Detail
            "DLD",  // Delivery Line Detail
            "DNA",  // Delivery Note Address
            "PYT",  // Payment Terms
            "ALD",  // Acknowledgment Line Detail
            "PCD",  // Price Catalogue Detail
            "LIN"   // Line Item
    );

    /**
     * Build message content using heuristics when no schema is available.
     * Uses segment repetition patterns and known TRADACOMS group triggers to detect groups.
     * A group is detected when:
     * 1. A segment tag appears multiple times, OR
     * 2. A segment tag is a known TRADACOMS group trigger
     * Segments following a group trigger are included in the group until
     * another group trigger is encountered or a non-group segment appears.
     */
    private List<SegmentOrGroup> buildContentWithHeuristics(List<Segment> segments) {
        List<SegmentOrGroup> content = new ArrayList<>();
        Map<String, Integer> segmentCounts = new HashMap<>();
        Map<String, Integer> groupLoopIndices = new HashMap<>();
        
        // First pass: count segment occurrences to identify potential group triggers
        for (Segment segment : segments) {
            segmentCounts.merge(segment.tag(), 1, Integer::sum);
        }
        
        // Identify group trigger tags:
        // 1. Known TRADACOMS group trigger tags (even if they appear only once)
        // 2. Segments that repeat (appear more than once) AND are known triggers
        // We prioritize known triggers to avoid false positives from repeating detail segments
        Set<String> groupTriggerTags = new HashSet<>();
        for (Map.Entry<String, Integer> entry : segmentCounts.entrySet()) {
            String tag = entry.getKey();
            // Only treat as group trigger if it's a known trigger
            // OR if it repeats and there's no known trigger in the message
            if (KNOWN_GROUP_TRIGGERS.contains(tag)) {
                groupTriggerTags.add(tag);
            }
        }
        
        // If no known triggers found, fall back to repetition-based detection
        if (groupTriggerTags.isEmpty()) {
            for (Map.Entry<String, Integer> entry : segmentCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    groupTriggerTags.add(entry.getKey());
                }
            }
        }
        
        // Second pass: build content with heuristic group detection
        int i = 0;
        while (i < segments.size()) {
            Segment segment = segments.get(i);
            String tag = segment.tag();
            
            if (groupTriggerTags.contains(tag)) {
                // This is a group trigger - collect related segments
                List<SegmentOrGroup> groupContent = new ArrayList<>();
                int loopIndex = groupLoopIndices.getOrDefault(tag, 0);
                groupLoopIndices.put(tag, loopIndex + 1);
                
                // Add trigger segment
                Segment triggerSegment = new Segment(
                        segment.tag(),
                        segment.elements(),
                        segment.lineNumber(),
                        segment.charPosition(),
                        segment.rawContent(),
                        true
                );
                groupContent.add(triggerSegment);
                i++;
                
                // Collect following segments until we hit another group trigger tag
                while (i < segments.size()) {
                    Segment nextSegment = segments.get(i);
                    String nextTag = nextSegment.tag();
                    // Stop when we see another group trigger (same or different)
                    if (groupTriggerTags.contains(nextTag)) {
                        break;
                    }
                    groupContent.add(nextSegment);
                    i++;
                }
                
                Group group = Group.of(tag, loopIndex, groupContent);
                content.add(group);
            } else {
                content.add(segment);
                i++;
            }
        }
        
        return content;
    }

    private record GroupParseResult(Group group, int nextIndex) {}


    // ========== Segment Parsing ==========

    private Segment parseNextSegment(Iterator<Token> tokens, Lexer lexer) {
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
                // Flush any pending components as a composite element
                if (!currentComponents.isEmpty()) {
                    elements.add(Element.of(new ArrayList<>(currentComponents)));
                    currentComponents.clear();
                    lastComponentIndex = -1;
                }
                // Unescape the value
                String unescaped = lexer.unescape(ev.value());
                elements.add(Element.of(unescaped));
                if (rawContent.length() > 0 && rawContent.charAt(rawContent.length() - 1) != TradacomsSyntax.TAG_SEPARATOR) {
                    rawContent.append(TradacomsSyntax.ELEMENT_SEPARATOR);
                }
                rawContent.append(ev.value());
            } else if (token instanceof Token.ComponentValue cv) {
                // If this is a new element (component index reset to 0), flush previous components
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
                // Flush any pending components as a composite element
                if (!currentComponents.isEmpty()) {
                    elements.add(Element.of(new ArrayList<>(currentComponents)));
                }
                
                rawContent.append(TradacomsSyntax.SEGMENT_TERMINATOR);
                
                if (tag != null) {
                    return new Segment(tag, elements, lineNumber, charPosition, rawContent.toString(), false);
                }
            } else if (token instanceof Token.EndOfInput) {
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

    /**
     * Extract message type from MHD segment.
     * MHD format: MHD=reference+type:version'
     * Example: MHD=1+CREDIT:9'
     */
    private String extractMessageType(Segment mhdSegment) {
        if (mhdSegment.elements().size() >= 2) {
            Element typeElement = mhdSegment.elements().get(1);
            // Type is the first component (e.g., "CREDIT" from "CREDIT:9")
            return typeElement.getValue();
        }
        return "UNKNOWN";
    }

    /**
     * Extract message control reference from MHD segment.
     * MHD format: MHD=reference+type:version'
     */
    private String extractMessageControlRef(Segment mhdSegment) {
        if (!mhdSegment.elements().isEmpty()) {
            return mhdSegment.elements().get(0).getValue();
        }
        return null;
    }

    /**
     * Extract sender ID from STX segment.
     * STX format: STX=syntax:version+sender_id:sender_name+receiver_id:receiver_name+date:time+reference'
     */
    private String extractSenderId(Segment stxSegment) {
        if (stxSegment.elements().size() >= 2) {
            Element senderElement = stxSegment.elements().get(1);
            return senderElement.getValue();
        }
        return null;
    }

    /**
     * Extract receiver ID from STX segment.
     */
    private String extractReceiverId(Segment stxSegment) {
        if (stxSegment.elements().size() >= 3) {
            Element receiverElement = stxSegment.elements().get(2);
            return receiverElement.getValue();
        }
        return null;
    }

    /**
     * Extract batch ID (transmission reference) from STX segment.
     */
    private String extractBatchId(Segment stxSegment) {
        if (stxSegment.elements().size() >= 5) {
            Element refElement = stxSegment.elements().get(4);
            return refElement.getValue();
        }
        return null;
    }

    /**
     * Extract timestamp from STX segment.
     * Date/time is in element 4 (0-indexed: 3) with format YYMMDD:HHMMSS
     */
    private Instant extractTimestamp(Segment stxSegment) {
        if (stxSegment.elements().size() >= 4) {
            Element dateTimeElement = stxSegment.elements().get(3);
            String dateStr = dateTimeElement.getComponent(0);
            String timeStr = dateTimeElement.getComponent(1);
            
            return parseDateTime(dateStr, timeStr);
        }
        return null;
    }

    /**
     * Parse TRADACOMS date (YYMMDD) and time (HHMMSS) into Instant.
     */
    private Instant parseDateTime(String dateStr, String timeStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        try {
            // Parse date (YYMMDD)
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyMMdd");
            LocalDate date = LocalDate.parse(dateStr, dateFormatter);
            
            // Parse time (HHMMSS) if available
            LocalTime time = LocalTime.MIDNIGHT;
            if (timeStr != null && !timeStr.isEmpty()) {
                if (timeStr.length() == 6) {
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HHmmss");
                    time = LocalTime.parse(timeStr, timeFormatter);
                } else if (timeStr.length() == 4) {
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HHmm");
                    time = LocalTime.parse(timeStr, timeFormatter);
                }
            }
            
            return date.atTime(time).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            // Return null if parsing fails - don't throw exception for optional field
            return null;
        }
    }

    /**
     * Parse a date element (YYMMDD format) to LocalDate.
     */
    public static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parse a time element (HHMMSS or HHMM format) to LocalTime.
     */
    public static LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        try {
            if (timeStr.length() == 6) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmmss");
                return LocalTime.parse(timeStr, formatter);
            } else if (timeStr.length() == 4) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");
                return LocalTime.parse(timeStr, formatter);
            }
            return null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
