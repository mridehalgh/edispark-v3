package com.edispark.edifact;

import java.util.ArrayList;
import java.util.List;

public final class EdifactParser {
    private static final int MAX_SEGMENTS = 100_000;

    public List<EdifactMessage> parse(String edi) {
        if (edi == null || edi.isBlank()) throw new IllegalArgumentException("EDIFACT payload is empty");
        char terminator = edi.startsWith("UNA") && edi.length() > 8 ? edi.charAt(8) : '\'';
        String[] raw = edi.split(java.util.regex.Pattern.quote(String.valueOf(terminator)));
        if (raw.length > MAX_SEGMENTS) throw new IllegalArgumentException("EDIFACT payload has too many segments");
        List<EdifactMessage> messages = new ArrayList<>();
        List<String> current = null;
        String reference = null;
        String type = null;
        String directory = null;
        for (String item : raw) {
            String segment = item.strip();
            if (segment.isEmpty() || segment.startsWith("UNA") || segment.startsWith("UNB") || segment.startsWith("UNZ")) continue;
            if (segment.startsWith("UNH+")) {
                if (current != null) throw new IllegalArgumentException("Nested UNH segment");
                String[] elements = segment.split("\\+", -1);
                if (elements.length < 3) throw new IllegalArgumentException("UNH is incomplete");
                String[] identification = elements[2].split(":", -1);
                if (identification.length < 4) throw new IllegalArgumentException("UNH message identity is incomplete");
                reference = elements[1];
                type = identification[0];
                directory = identification[1] + identification[2];
                current = new ArrayList<>();
                current.add(segment);
            } else if (segment.startsWith("UNT+")) {
                if (current == null) throw new IllegalArgumentException("UNT has no UNH");
                current.add(segment);
                messages.add(new EdifactMessage(reference, type, directory, current));
                current = null;
            } else if (current != null) {
                current.add(segment);
            }
        }
        if (current != null) throw new IllegalArgumentException("EDIFACT message is missing UNT");
        if (messages.isEmpty()) throw new IllegalArgumentException("EDIFACT payload contains no messages");
        return List.copyOf(messages);
    }
}
