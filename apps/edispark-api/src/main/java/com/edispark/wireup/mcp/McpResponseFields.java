package com.edispark.wireup.mcp;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class McpResponseFields {
    private McpResponseFields() { }

    static String safeCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,79}") ? value : "UNCLASSIFIED";
    }

    static String safePath(String value) {
        return value != null && value.matches("[A-Za-z0-9_.$/\\[\\]:-]{1,160}") ? value : "/";
    }

    static Map<String, Object> fields(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            Object value = entries[index + 1];
            if (value instanceof UUID || value instanceof Instant || value instanceof Enum<?>) value = value.toString();
            if (value != null) result.put((String) entries[index], value);
        }
        return result;
    }

    static final class EvidenceMissing extends RuntimeException { }
}
