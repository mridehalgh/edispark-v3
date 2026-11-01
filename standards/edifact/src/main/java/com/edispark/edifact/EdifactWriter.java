package com.edispark.edifact;

import java.util.List;

public final class EdifactWriter {
    public String interchange(String sender, String receiver, String controlReference, List<EdifactMessage> messages) {
        requireText(sender, "sender"); requireText(receiver, "receiver"); requireText(controlReference, "controlReference");
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("At least one message is required");
        StringBuilder result = new StringBuilder("UNB+UNOA:3+").append(sender).append('+').append(receiver)
                .append("+000101:0000+").append(controlReference).append('\'');
        messages.forEach(message -> message.segments().forEach(segment -> result.append(segment).append('\'')));
        return result.append("UNZ+").append(messages.size()).append('+').append(controlReference).append('\'').toString();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.indexOf('\'') >= 0 || value.indexOf('+') >= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
