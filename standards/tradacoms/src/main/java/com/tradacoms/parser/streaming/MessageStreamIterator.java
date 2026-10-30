package com.tradacoms.parser.streaming;

import com.tradacoms.parser.ParserConfig;
import com.tradacoms.parser.model.*;
import com.tradacoms.parser.schema.MessageSchema;

import java.io.InputStream;
import java.util.*;

/**
 * Iterator that streams messages from a TRADACOMS batch one at a time.
 * Buffers segments for a single message and emits complete Message objects.
 * 
 * <p>This provides a balance between memory efficiency (only one message
 * buffered at a time) and ease of use (complete Message objects).
 */
public final class MessageStreamIterator implements Iterator<Message> {

    private final BatchEventReader eventReader;
    private final ParserConfig config;
    
    private Message nextMessage = null;
    private boolean finished = false;
    
    // Current message state
    private int currentMessageIndex = -1;
    private String currentMessageType = null;
    private List<SegmentOrGroup> currentContent = new ArrayList<>();
    private Segment currentHeader = null;
    private Segment currentTrailer = null;
    private String currentControlRef = null;
    
    // Group building state
    private final Deque<GroupBuilder> groupStack = new ArrayDeque<>();

    public MessageStreamIterator(InputStream input, ParserConfig config, Map<String, MessageSchema> schemaRegistry) {
        this.config = config != null ? config : ParserConfig.defaults();
        this.eventReader = new BatchEventReader(input, this.config, schemaRegistry);
    }

    @Override
    public boolean hasNext() {
        if (nextMessage != null) {
            return true;
        }
        if (finished) {
            return false;
        }
        nextMessage = readNextMessage();
        return nextMessage != null;
    }

    @Override
    public Message next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more messages");
        }
        Message message = nextMessage;
        nextMessage = null;
        return message;
    }

    private Message readNextMessage() {
        while (eventReader.hasNext()) {
            BatchEvent event = eventReader.next();
            
            if (event instanceof BatchEvent.StartBatch) {
                // Skip batch start
                continue;
            }
            
            if (event instanceof BatchEvent.StartMessage startMsg) {
                // Start building a new message
                currentMessageIndex = startMsg.index();
                currentMessageType = startMsg.type();
                currentContent = new ArrayList<>();
                currentHeader = null;
                currentTrailer = null;
                currentControlRef = null;
                groupStack.clear();
                continue;
            }
            
            if (event instanceof BatchEvent.StartGroup startGroup) {
                // Start a new group
                GroupBuilder builder = new GroupBuilder(startGroup.groupId(), startGroup.loopIndex());
                groupStack.push(builder);
                continue;
            }
            
            if (event instanceof BatchEvent.SegmentRead segmentRead) {
                Segment segment = segmentRead.segment();
                
                // Check if this is MHD (header)
                if ("MHD".equals(segment.tag())) {
                    currentHeader = segment;
                    currentControlRef = extractControlRef(segment);
                    continue;
                }
                
                // Check if this is MTR (trailer)
                if ("MTR".equals(segment.tag())) {
                    currentTrailer = segment;
                    continue;
                }
                
                // Add segment to current group or message content
                if (!groupStack.isEmpty()) {
                    groupStack.peek().addContent(segment);
                } else {
                    currentContent.add(segment);
                }
                continue;
            }
            
            if (event instanceof BatchEvent.EndGroup endGroup) {
                // Complete the group and add to parent
                if (!groupStack.isEmpty()) {
                    GroupBuilder builder = groupStack.pop();
                    Group group = builder.build();
                    
                    if (!groupStack.isEmpty()) {
                        // Nested group - add to parent group
                        groupStack.peek().addContent(group);
                    } else {
                        // Top-level group - add to message content
                        currentContent.add(group);
                    }
                }
                continue;
            }
            
            if (event instanceof BatchEvent.EndMessage endMsg) {
                // Build and return the complete message
                // Apply message type filtering
                if (config.shouldIncludeMessageType(currentMessageType)) {
                    return new Message(
                            currentMessageType,
                            currentMessageIndex,
                            currentControlRef,
                            currentContent,
                            Map.of(),
                            currentHeader,
                            currentTrailer
                    );
                }
                // Message filtered out - continue to next
                continue;
            }
            
            if (event instanceof BatchEvent.EndBatch) {
                finished = true;
                eventReader.close();
                return null;
            }
        }
        
        finished = true;
        return null;
    }

    private String extractControlRef(Segment mhdSegment) {
        if (!mhdSegment.elements().isEmpty()) {
            return mhdSegment.elements().get(0).getValue();
        }
        return null;
    }

    /**
     * Helper class for building groups during streaming.
     */
    private static final class GroupBuilder {
        private final String groupId;
        private final int loopIndex;
        private final List<SegmentOrGroup> content = new ArrayList<>();

        GroupBuilder(String groupId, int loopIndex) {
            this.groupId = groupId;
            this.loopIndex = loopIndex;
        }

        void addContent(SegmentOrGroup item) {
            content.add(item);
        }

        Group build() {
            return Group.of(groupId, loopIndex, content);
        }
    }
}
