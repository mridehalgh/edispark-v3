package com.edispark.as2simulator;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public final class SimulatorStore {
    private final CopyOnWriteArrayList<SimulatedMessage> messages = new CopyOnWriteArrayList<>();

    public void add(SimulatedMessage message) { messages.add(0, message); }
    public List<SimulatedMessage> list() { return List.copyOf(messages); }
    public void clear() { messages.clear(); }

    public record SimulatedMessage(String direction, String messageId, String sender, String receiver,
                                   String filename, String contentType, long size, Instant timestamp) {}
}
