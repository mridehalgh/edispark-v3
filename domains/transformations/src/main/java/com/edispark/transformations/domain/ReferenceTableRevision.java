package com.edispark.transformations.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An immutable lookup table snapshot. Identifiers are kept as exact text. */
public record ReferenceTableRevision(UUID id, UUID previousRevisionId, String name, String keyColumn,
        List<Map<String, String>> rows) {
    private static final int MAX_ROWS = 1_000;
    private static final int MAX_COLUMNS = 20;
    private static final int MAX_CELL_LENGTH = 256;

    public ReferenceTableRevision {
        if (id == null || name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("A reference table needs a name of up to 120 characters.");
        }
        if (keyColumn == null || keyColumn.isBlank() || rows == null || rows.isEmpty() || rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Provide a key column and between 1 and 1,000 reference rows.");
        }
        if (rows.getFirst() == null) throw new IllegalArgumentException("A reference row is missing.");
        var columns = rows.getFirst().keySet();
        if (!columns.contains(keyColumn) || columns.size() > MAX_COLUMNS || columns.stream().anyMatch(
                column -> column == null || !column.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}"))) {
            throw new IllegalArgumentException("Reference columns must use letters, digits or underscores and include the key.");
        }
        var keys = new HashSet<String>();
        for (var row : rows) {
            if (row == null || !row.keySet().equals(columns) || row.values().stream()
                    .anyMatch(value -> value == null || value.length() > MAX_CELL_LENGTH)) {
                throw new IllegalArgumentException("Reference rows must have the same columns and text values up to 256 characters.");
            }
            String key = row.get(keyColumn);
            if (key.isBlank() || !keys.add(key)) {
                throw new IllegalArgumentException("Reference keys must be nonblank and unique.");
            }
        }
        rows = rows.stream().map(Map::copyOf).toList();
    }

    public Map<String, String> requireRow(String key) {
        return rows.stream().filter(row -> row.get(keyColumn).equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Reference table " + id + " has no match for " + keyColumn + "=" + key));
    }
}
