package com.edispark.integrationapplication.inbound;

import com.edispark.partnerships.domain.model.EdiStandard;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class InboundEdiBatchSplitter {
    private final int maximumMessages;

    InboundEdiBatchSplitter(int maximumMessages) {
        if (maximumMessages < 1) throw new IllegalArgumentException("maximumMessages must be positive");
        this.maximumMessages = maximumMessages;
    }

    List<InboundMessage> split(String edi) {
        List<String> segments = segments(edi);
        if (segments.stream().anyMatch(segment -> segment.startsWith("UNH+"))) return edifact(segments);
        if (segments.stream().anyMatch(segment -> segment.startsWith("MHD="))) return tradacoms(segments);
        throw new IllegalArgumentException("Inbound EDI payload contains no supported messages");
    }

    private List<InboundMessage> edifact(List<String> segments) {
        List<String> context = segments.stream()
                .filter(segment -> segment.startsWith("UNA") || segment.startsWith("UNB+") || segment.startsWith("UNG+"))
                .toList();
        List<Block> blocks = blocks(segments, "UNH+", "UNT+");
        return messages(EdiStandard.EDIFACT, context, blocks);
    }

    private List<InboundMessage> tradacoms(List<String> segments) {
        List<Block> blocks = blocks(segments, "MHD=", "MTR=");
        List<String> context = new ArrayList<>();
        segments.stream().filter(segment -> segment.startsWith("STX=")).findFirst().ifPresent(context::add);
        List<Block> business = new ArrayList<>();
        for (Block block : blocks) {
            String type = component(element(block.segments().getFirst(), 1), 0).toUpperCase();
            if (type.equals("ORDERS") || type.equals("INVOIC")) business.add(block);
            else context.addAll(block.segments());
        }
        return messages(EdiStandard.TRADACOMS, context, business);
    }

    private List<InboundMessage> messages(EdiStandard standard, List<String> context, List<Block> blocks) {
        if (blocks.isEmpty()) throw new IllegalArgumentException("Inbound EDI payload contains no routable messages");
        if (blocks.size() > maximumMessages) {
            throw new IllegalArgumentException("Inbound EDI payload exceeds the configured message limit");
        }
        List<InboundMessage> messages = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            List<String> mappingSegments = new ArrayList<>(context);
            mappingSegments.addAll(block.segments());
            messages.add(new InboundMessage(standard, index + 1, blocks.size(), block.reference(),
                    List.copyOf(mappingSegments), bytes(block.segments())));
        }
        return List.copyOf(messages);
    }

    private List<Block> blocks(List<String> segments, String startPrefix, String endPrefix) {
        List<Block> blocks = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            if (!segments.get(index).startsWith(startPrefix)) continue;
            int end = index + 1;
            while (end < segments.size() && !segments.get(end).startsWith(endPrefix)) {
                if (segments.get(end).startsWith(startPrefix)) {
                    throw new IllegalArgumentException("Inbound EDI payload has nested message headers");
                }
                end++;
            }
            if (end == segments.size()) throw new IllegalArgumentException("Inbound EDI message trailer is missing");
            String headerReference = element(segments.get(index), 0);
            String trailerReference = element(segments.get(end), 1);
            if (!trailerReference.isBlank() && !headerReference.equals(trailerReference)) {
                throw new IllegalArgumentException("Inbound EDI message references do not match");
            }
            blocks.add(new Block(segments.subList(index, end + 1), headerReference));
            index = end;
        }
        return List.copyOf(blocks);
    }

    private List<String> segments(String edi) {
        return Arrays.stream(edi.replace("\r", "").replace("\n", "").split("'"))
                .map(String::strip).filter(value -> !value.isBlank()).toList();
    }

    private String element(String segment, int index) {
        String[] elements = segment.substring(4).split("\\+", -1);
        return index < elements.length ? elements[index] : "";
    }

    private String component(String value, int index) {
        String[] components = value.split(":", -1);
        return index < components.length ? components[index] : "";
    }

    private byte[] bytes(List<String> segments) {
        return (String.join("'", segments) + "'").getBytes(StandardCharsets.US_ASCII);
    }

    record InboundMessage(EdiStandard standard, int ordinal, int messageCount, String sourceReference,
                          List<String> mappingSegments, byte[] rawMessage) { }
    private record Block(List<String> segments, String reference) { }
}
