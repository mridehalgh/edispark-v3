package com.edispark.wireup.mcp;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

record McpArguments(Map<String, Object> values) {
    String text(String key) {
        if (values.get(key) instanceof String value && !value.isBlank()) return value;
        throw new IllegalArgumentException("A string argument is required");
    }

    String optionalText(String key) {
        return values.containsKey(key) ? text(key) : null;
    }

    UUID id(String key) {
        return UUID.fromString(text(key));
    }

    UUID optionalId(String key) {
        return values.containsKey(key) ? id(key) : null;
    }

    Instant optionalInstant(String key) {
        return values.containsKey(key) ? Instant.parse(text(key)) : null;
    }

    <E extends Enum<E>> E optionalEnum(String key, Class<E> type) {
        return values.containsKey(key) ? Enum.valueOf(type, text(key)) : null;
    }

    String cursorBinding(String tool) {
        var filters = new TreeMap<>(values);
        filters.remove("cursor");
        return filters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().toString().length() + ":" + entry.getValue())
                .collect(Collectors.joining("\n", tool + "\n", ""));
    }

    UUID uuidPosition(McpCursor cursor, String tenant, String binding) {
        return values.containsKey("cursor") ? cursor.decode(text("cursor"), tenant, binding) : null;
    }

    String pagePosition(McpCursor cursor, String tenant, String binding) {
        return values.containsKey("cursor") ? cursor.unwrapPage(text("cursor"), tenant, binding) : null;
    }
}
