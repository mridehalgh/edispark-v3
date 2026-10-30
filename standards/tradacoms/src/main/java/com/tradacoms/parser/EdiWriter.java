package com.tradacoms.parser;

import com.tradacoms.parser.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Writer for serializing TRADACOMS model objects to EDI format.
 * Supports writing batches, messages, and individual segments.
 */
public final class EdiWriter {

    private final WriterConfig config;
    private final Serializer serializer;

    public EdiWriter() {
        this(WriterConfig.defaults());
    }

    public EdiWriter(WriterConfig config) {
        this.config = config != null ? config : WriterConfig.defaults();
        this.serializer = new Serializer(this.config);
    }

    /**
     * Writes a Batch to the output stream.
     *
     * @param batch the batch to write
     * @param output the output stream
     * @throws IOException if an I/O error occurs
     */
    public void writeBatch(Batch batch, OutputStream output) throws IOException {
        writeBatch(batch, output, config);
    }

    /**
     * Writes a Batch to the output stream with custom configuration.
     *
     * @param batch the batch to write
     * @param output the output stream
     * @param config writer configuration
     * @throws IOException if an I/O error occurs
     */
    public void writeBatch(Batch batch, OutputStream output, WriterConfig config) throws IOException {
        Objects.requireNonNull(batch, "batch must not be null");
        Objects.requireNonNull(output, "output must not be null");
        
        Serializer ser = config != null ? new Serializer(config) : this.serializer;
        WriterConfig cfg = config != null ? config : this.config;
        
        Writer writer = new OutputStreamWriter(output, cfg.getCharset());
        
        // Write STX envelope
        if (cfg.isRoundTripMode() && batch.getRawHeader() != null) {
            // Preserve original STX segment in round-trip mode
            writer.write(ser.serializeSegment(batch.getRawHeader()));
        } else {
            // Generate STX segment
            Segment stx = buildStxSegment(batch, cfg);
            writer.write(ser.serializeSegment(stx));
        }
        
        // Write all messages
        int segmentCount = 1; // STX counts as 1
        for (Message message : batch.getMessages()) {
            segmentCount += writeMessage(message, writer, ser, cfg);
        }
        
        // Write END envelope
        if (cfg.isRoundTripMode() && batch.getRawTrailer() != null) {
            // Preserve original END segment in round-trip mode
            writer.write(ser.serializeSegment(batch.getRawTrailer()));
        } else {
            // Generate END segment with computed totals
            Segment end = buildEndSegment(batch, segmentCount + 1, cfg);
            writer.write(ser.serializeSegment(end));
        }
        
        writer.flush();
    }

    /**
     * Writes a Message to the output stream.
     *
     * @param message the message to write
     * @param output the output stream
     * @throws IOException if an I/O error occurs
     */
    public void writeMessage(Message message, OutputStream output) throws IOException {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(output, "output must not be null");
        
        Writer writer = new OutputStreamWriter(output, config.getCharset());
        writeMessage(message, writer, serializer, config);
        writer.flush();
    }

    /**
     * Serializes a Batch to a string.
     *
     * @param batch the batch to serialize
     * @return the serialized TRADACOMS string
     */
    public String serializeBatch(Batch batch) {
        Objects.requireNonNull(batch, "batch must not be null");
        
        StringBuilder sb = new StringBuilder();
        
        // Write STX envelope
        if (config.isRoundTripMode() && batch.getRawHeader() != null) {
            sb.append(serializer.serializeSegment(batch.getRawHeader()));
        } else {
            Segment stx = buildStxSegment(batch, config);
            sb.append(serializer.serializeSegment(stx));
        }
        
        // Write all messages
        int segmentCount = 1; // STX counts as 1
        for (Message message : batch.getMessages()) {
            segmentCount += serializeMessage(message, sb);
        }
        
        // Write END envelope
        if (config.isRoundTripMode() && batch.getRawTrailer() != null) {
            sb.append(serializer.serializeSegment(batch.getRawTrailer()));
        } else {
            Segment end = buildEndSegment(batch, segmentCount + 1, config);
            sb.append(serializer.serializeSegment(end));
        }
        
        return sb.toString();
    }

    /**
     * Serializes a Message to a string.
     *
     * @param message the message to serialize
     * @return the serialized TRADACOMS string
     */
    public String serializeMessage(Message message) {
        Objects.requireNonNull(message, "message must not be null");
        
        StringBuilder sb = new StringBuilder();
        serializeMessage(message, sb);
        return sb.toString();
    }

    // ========== Internal Methods ==========

    /**
     * Writes a message to the writer and returns the segment count.
     */
    private int writeMessage(Message message, Writer writer, Serializer ser, WriterConfig cfg) throws IOException {
        int segmentCount = 0;
        
        // Write MHD (message header)
        if (cfg.isRoundTripMode() && message.getHeader() != null) {
            writer.write(ser.serializeSegment(message.getHeader()));
        } else {
            Segment mhd = buildMhdSegment(message);
            writer.write(ser.serializeSegment(mhd));
        }
        segmentCount++;
        
        // Write message content (segments and groups)
        for (SegmentOrGroup item : message.getContent()) {
            segmentCount += writeSegmentOrGroup(item, writer, ser);
        }
        
        // Write MTR (message trailer)
        if (cfg.isRoundTripMode() && message.getTrailer() != null) {
            writer.write(ser.serializeSegment(message.getTrailer()));
        } else {
            // MTR contains segment count including MHD and MTR
            Segment mtr = buildMtrSegment(segmentCount + 1);
            writer.write(ser.serializeSegment(mtr));
        }
        segmentCount++;
        
        return segmentCount;
    }

    /**
     * Writes a SegmentOrGroup to the writer and returns the segment count.
     */
    private int writeSegmentOrGroup(SegmentOrGroup item, Writer writer, Serializer ser) throws IOException {
        if (item instanceof Segment segment) {
            writer.write(ser.serializeSegment(segment));
            return 1;
        } else if (item instanceof Group group) {
            int count = 0;
            for (SegmentOrGroup child : group.getContent()) {
                count += writeSegmentOrGroup(child, writer, ser);
            }
            return count;
        }
        return 0;
    }

    /**
     * Serializes a message to the StringBuilder and returns the segment count.
     */
    private int serializeMessage(Message message, StringBuilder sb) {
        int segmentCount = 0;
        
        // Write MHD (message header)
        if (config.isRoundTripMode() && message.getHeader() != null) {
            sb.append(serializer.serializeSegment(message.getHeader()));
        } else {
            Segment mhd = buildMhdSegment(message);
            sb.append(serializer.serializeSegment(mhd));
        }
        segmentCount++;
        
        // Write message content (segments and groups)
        for (SegmentOrGroup item : message.getContent()) {
            segmentCount += serializeSegmentOrGroup(item, sb);
        }
        
        // Write MTR (message trailer)
        if (config.isRoundTripMode() && message.getTrailer() != null) {
            sb.append(serializer.serializeSegment(message.getTrailer()));
        } else {
            Segment mtr = buildMtrSegment(segmentCount + 1);
            sb.append(serializer.serializeSegment(mtr));
        }
        segmentCount++;
        
        return segmentCount;
    }

    /**
     * Serializes a SegmentOrGroup to the StringBuilder and returns the segment count.
     */
    private int serializeSegmentOrGroup(SegmentOrGroup item, StringBuilder sb) {
        if (item instanceof Segment segment) {
            sb.append(serializer.serializeSegment(segment));
            return 1;
        } else if (item instanceof Group group) {
            int count = 0;
            for (SegmentOrGroup child : group.getContent()) {
                count += serializeSegmentOrGroup(child, sb);
            }
            return count;
        }
        return 0;
    }

    // ========== Segment Builders ==========

    /**
     * Builds an STX (Start of Transmission) segment.
     * Format: STX=ANAA:1+sender:name+receiver:name+date:time+reference'
     */
    private Segment buildStxSegment(Batch batch, WriterConfig cfg) {
        String senderId = batch.getSenderId() != null ? batch.getSenderId() : "";
        String receiverId = batch.getReceiverId() != null ? batch.getReceiverId() : "";
        String batchId = batch.getBatchId() != null ? batch.getBatchId() : "1";
        
        // Format timestamp
        String dateStr = "";
        String timeStr = "";
        Instant timestamp = batch.getCreationTimestamp();
        if (timestamp != null) {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);
            dateStr = dateFormatter.format(timestamp);
            timeStr = timeFormatter.format(timestamp);
        }
        
        return Segment.of(TradacomsSyntax.STX, List.of(
                Element.of(List.of("ANAA", "1")),           // Syntax identifier and version
                Element.of(List.of(senderId, "")),          // Sender ID and name
                Element.of(List.of(receiverId, "")),        // Receiver ID and name
                Element.of(List.of(dateStr, timeStr)),      // Date and time
                Element.of(batchId)                          // Transmission reference
        ));
    }

    /**
     * Builds an END (End of Transmission) segment.
     * Format: END=messageCount'
     */
    private Segment buildEndSegment(Batch batch, int totalSegmentCount, WriterConfig cfg) {
        int messageCount = batch.getMessages().size();
        
        return Segment.of(TradacomsSyntax.END, List.of(
                Element.of(String.valueOf(messageCount))
        ));
    }

    /**
     * Builds an MHD (Message Header) segment.
     * Format: MHD=reference+type:version'
     */
    private Segment buildMhdSegment(Message message) {
        String controlRef = message.getMessageControlRef() != null 
                ? message.getMessageControlRef() 
                : String.valueOf(message.getMessageIndexInBatch() + 1);
        String messageType = message.getMessageType();
        
        return Segment.of(TradacomsSyntax.MHD, List.of(
                Element.of(controlRef),
                Element.of(List.of(messageType, "9"))  // Type and version
        ));
    }

    /**
     * Builds an MTR (Message Trailer) segment.
     * Format: MTR=segmentCount'
     */
    private Segment buildMtrSegment(int segmentCount) {
        return Segment.of(TradacomsSyntax.MTR, List.of(
                Element.of(String.valueOf(segmentCount))
        ));
    }

    /**
     * Returns the configuration used by this writer.
     */
    public WriterConfig getConfig() {
        return config;
    }

    /**
     * Returns the serializer used by this writer.
     */
    public Serializer getSerializer() {
        return serializer;
    }

    // ========== Split Writing Methods ==========

    /**
     * Writes a batch split according to the specified strategy.
     * Groups messages by the strategy and writes each group to a separate output.
     * Each output file contains a valid TRADACOMS envelope.
     *
     * @param batch the batch to split and write
     * @param strategy the splitting strategy
     * @param target the output target
     * @return list of written artifacts describing each output
     * @throws IOException if an I/O error occurs
     * 
     * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.7
     */
    public List<WrittenArtifact> writeSplit(Batch batch, SplitStrategy strategy, OutputTarget target) throws IOException {
        return writeSplit(batch, strategy, target, config);
    }

    /**
     * Writes a batch split according to the specified strategy with custom configuration.
     *
     * @param batch the batch to split and write
     * @param strategy the splitting strategy
     * @param target the output target
     * @param config writer configuration
     * @return list of written artifacts describing each output
     * @throws IOException if an I/O error occurs
     */
    public List<WrittenArtifact> writeSplit(Batch batch, SplitStrategy strategy, OutputTarget target, WriterConfig config) throws IOException {
        Objects.requireNonNull(batch, "batch must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(target, "target must not be null");

        WriterConfig cfg = config != null ? config : this.config;

        // Group messages by strategy
        Map<String, List<MessageWithIndex>> groupedMessages = groupMessagesByStrategy(batch, strategy);

        // Write each group
        List<WrittenArtifact> artifacts = new ArrayList<>();
        int outputIndex = 0;

        // Sort keys for deterministic ordering if configured
        List<String> sortedKeys = new ArrayList<>(groupedMessages.keySet());
        if (cfg.isDeterministicOrdering()) {
            Collections.sort(sortedKeys);
        }

        for (String groupKey : sortedKeys) {
            List<MessageWithIndex> messagesWithIndex = groupedMessages.get(groupKey);
            
            // Create a sub-batch for this group
            List<Message> groupMessages = reindexMessages(messagesWithIndex);
            Batch subBatch = createSubBatch(batch, groupMessages);

            // Serialize the sub-batch
            String content = serializeBatchInternal(subBatch, cfg);

            // Generate correlation ID
            String correlationId = generateCorrelationId(batch, groupKey, outputIndex);

            // Collect message IDs
            List<String> messageIds = collectMessageIds(messagesWithIndex);

            // Determine message type for filename template
            String messageType = groupMessages.isEmpty() ? null : groupMessages.get(0).getMessageType();

            // Write to target
            WrittenArtifact artifact = writeToTarget(
                    target, content, correlationId, messageIds, 
                    groupKey, outputIndex, messageType, batch.getBatchId(), cfg
            );
            artifacts.add(artifact);

            outputIndex++;
        }

        return artifacts;
    }

    /**
     * Groups messages by the split strategy.
     */
    private Map<String, List<MessageWithIndex>> groupMessagesByStrategy(Batch batch, SplitStrategy strategy) {
        Map<String, List<MessageWithIndex>> grouped = new LinkedHashMap<>();
        
        List<Message> messages = batch.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            String key = strategy.getGroupKey(message, i);
            
            grouped.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new MessageWithIndex(message, i));
        }
        
        return grouped;
    }

    /**
     * Reindexes messages for a sub-batch, assigning new sequential indices.
     */
    private List<Message> reindexMessages(List<MessageWithIndex> messagesWithIndex) {
        List<Message> reindexed = new ArrayList<>();
        
        for (int i = 0; i < messagesWithIndex.size(); i++) {
            Message original = messagesWithIndex.get(i).message();
            Message reindexedMessage = new Message(
                    original.getMessageType(),
                    i,  // New index in sub-batch
                    original.getMessageControlRef(),
                    original.getContent(),
                    original.getRoutingKeys(),
                    original.getHeader(),
                    original.getTrailer()
            );
            reindexed.add(reindexedMessage);
        }
        
        return reindexed;
    }

    /**
     * Creates a sub-batch from the original batch with a subset of messages.
     */
    private Batch createSubBatch(Batch original, List<Message> messages) {
        return new Batch(
                original.getBatchId(),
                original.getSenderId(),
                original.getReceiverId(),
                original.getCreationTimestamp(),
                messages,
                null,  // Don't preserve raw header for split batches
                null,  // Don't preserve raw trailer for split batches
                original.getSourceInfo()
        );
    }

    /**
     * Serializes a batch to a string using the internal serialization logic.
     */
    private String serializeBatchInternal(Batch batch, WriterConfig cfg) {
        Serializer ser = new Serializer(cfg);
        StringBuilder sb = new StringBuilder();

        // Write STX envelope
        Segment stx = buildStxSegment(batch, cfg);
        sb.append(ser.serializeSegment(stx));

        // Write all messages
        int segmentCount = 1; // STX counts as 1
        for (Message message : batch.getMessages()) {
            segmentCount += serializeMessageInternal(message, sb, ser, cfg);
        }

        // Write END envelope
        Segment end = buildEndSegment(batch, segmentCount + 1, cfg);
        sb.append(ser.serializeSegment(end));

        return sb.toString();
    }

    /**
     * Serializes a message to the StringBuilder and returns the segment count.
     */
    private int serializeMessageInternal(Message message, StringBuilder sb, Serializer ser, WriterConfig cfg) {
        int segmentCount = 0;

        // Write MHD (message header)
        Segment mhd = buildMhdSegment(message);
        sb.append(ser.serializeSegment(mhd));
        segmentCount++;

        // Write message content (segments and groups)
        for (SegmentOrGroup item : message.getContent()) {
            segmentCount += serializeSegmentOrGroupInternal(item, sb, ser);
        }

        // Write MTR (message trailer)
        Segment mtr = buildMtrSegment(segmentCount + 1);
        sb.append(ser.serializeSegment(mtr));
        segmentCount++;

        return segmentCount;
    }

    /**
     * Serializes a SegmentOrGroup to the StringBuilder and returns the segment count.
     */
    private int serializeSegmentOrGroupInternal(SegmentOrGroup item, StringBuilder sb, Serializer ser) {
        if (item instanceof Segment segment) {
            sb.append(ser.serializeSegment(segment));
            return 1;
        } else if (item instanceof Group group) {
            int count = 0;
            for (SegmentOrGroup child : group.getContent()) {
                count += serializeSegmentOrGroupInternal(child, sb, ser);
            }
            return count;
        }
        return 0;
    }

    /**
     * Generates a correlation ID for a split output.
     */
    private String generateCorrelationId(Batch batch, String groupKey, int outputIndex) {
        String batchId = batch.getBatchId() != null ? batch.getBatchId() : "batch";
        return String.format("%s_%s_%d", batchId, groupKey, outputIndex);
    }

    /**
     * Collects message IDs from messages with their original indices.
     */
    private List<String> collectMessageIds(List<MessageWithIndex> messagesWithIndex) {
        return messagesWithIndex.stream()
                .map(mwi -> {
                    Message m = mwi.message();
                    String controlRef = m.getMessageControlRef();
                    return controlRef != null ? controlRef : String.valueOf(mwi.originalIndex() + 1);
                })
                .collect(Collectors.toList());
    }

    /**
     * Writes content to the specified target.
     */
    private WrittenArtifact writeToTarget(
            OutputTarget target,
            String content,
            String correlationId,
            List<String> messageIds,
            String groupKey,
            int outputIndex,
            String messageType,
            String batchId,
            WriterConfig cfg
    ) throws IOException {
        if (target instanceof OutputTarget.Directory dirTarget) {
            // Write to file
            String filename = dirTarget.template().generate(groupKey, outputIndex, messageType, batchId);
            Path outputPath = dirTarget.dir().resolve(filename);
            
            // Ensure parent directory exists
            Files.createDirectories(outputPath.getParent());
            
            // Write content
            byte[] bytes = content.getBytes(cfg.getCharset());
            Files.write(outputPath, bytes);
            
            return WrittenArtifact.ofFile(outputPath, correlationId, messageIds, bytes.length);
            
        } else if (target instanceof OutputTarget.Callback callbackTarget) {
            // Create artifact with content
            WrittenArtifact artifact = WrittenArtifact.ofContent(correlationId, messageIds, content);
            
            // Invoke callback
            callbackTarget.sink().accept(artifact);
            
            return artifact;
        }
        
        throw new IllegalArgumentException("Unknown OutputTarget type: " + target.getClass());
    }

    /**
     * Internal record to track messages with their original batch index.
     */
    private record MessageWithIndex(Message message, int originalIndex) {}
}
