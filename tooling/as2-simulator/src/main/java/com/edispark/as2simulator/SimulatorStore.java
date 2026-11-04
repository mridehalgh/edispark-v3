package com.edispark.as2simulator;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public final class SimulatorStore {
    static final int MAX_MESSAGES = 100;
    static final int MAX_PAYLOAD_BYTES = 1_048_576;

    private final CopyOnWriteArrayList<StoredMessage> messages = new CopyOnWriteArrayList<>();

    public SimulatedMessage add(String direction, String messageId, String sender, String receiver, String filename,
            String contentType, byte[] content, Instant timestamp) {
        if (content.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Simulator payload exceeds the 1 MiB inspection limit");
        }
        byte[] retained = content.clone();
        SimulatedMessage metadata = new SimulatedMessage(UUID.randomUUID(), direction, messageId, sender, receiver,
                filename, contentType, retained.length, sha256(retained), timestamp);
        messages.add(0, new StoredMessage(metadata, retained));
        while (messages.size() > MAX_MESSAGES) messages.removeLast();
        return metadata;
    }

    public List<SimulatedMessage> list() { return messages.stream().map(StoredMessage::metadata).toList(); }

    public Optional<byte[]> content(UUID evidenceId) {
        return messages.stream().filter(item -> item.metadata().evidenceId().equals(evidenceId)).findFirst()
                .map(item -> item.content().clone());
    }

    public void clear() { messages.clear(); }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record SimulatedMessage(UUID evidenceId, String direction, String messageId, String sender,
            String receiver, String filename, String contentType, long size, String sha256, Instant timestamp) { }

    private record StoredMessage(SimulatedMessage metadata, byte[] content) { }
}
