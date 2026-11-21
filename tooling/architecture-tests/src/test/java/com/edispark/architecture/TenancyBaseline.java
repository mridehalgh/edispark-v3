package com.edispark.architecture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class TenancyBaseline {
    private TenancyBaseline() { }

    record Entry(String key, String finding, String reason) {
        Entry {
            if (key.isBlank() || !finding.matches("MT 0[1-7]") || reason.isBlank()) {
                throw new IllegalArgumentException("Baseline entries need a rule/member key, audit finding, and reason");
            }
        }
    }

    static List<Entry> load() throws IOException {
        return lines("/tenancy-baseline.tsv").stream().map(TenancyBaseline::parseEntry).toList();
    }

    static Map<String, String> classifications() throws IOException {
        return lines("/tenancy-port-classifications.tsv").stream()
                .map(line -> columns(line, 3))
                .peek(TenancyBaseline::validateClassification)
                .collect(Collectors.toUnmodifiableMap(parts -> parts[0], parts -> parts[1]));
    }

    static void verify(Set<String> actual, List<Entry> baseline) {
        Set<String> expected = new HashSet<>();
        baseline.forEach(entry -> {
            if (!expected.add(entry.key())) throw new IllegalArgumentException("Duplicate baseline entry: " + entry.key());
        });
        Set<String> added = difference(actual, expected);
        Set<String> resolved = difference(expected, actual);
        if (!added.isEmpty() || !resolved.isEmpty()) {
            throw new AssertionError("New violations (fix code; do not regenerate the baseline):\n"
                    + String.join("\n", added) + "\nResolved baseline entries (remove them):\n" + String.join("\n", resolved));
        }
    }

    static List<String> lines(String resource) throws IOException {
        var input = Objects.requireNonNull(TenancyBaseline.class.getResourceAsStream(resource), "Missing " + resource);
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).filter(line -> !line.startsWith("#")).toList();
        }
    }

    private static Entry parseEntry(String line) {
        String[] parts = columns(line, 4);
        return new Entry(parts[0] + "\t" + parts[1], parts[2], parts[3]);
    }

    private static String[] columns(String line, int count) {
        String[] parts = line.split("\t", -1);
        if (parts.length != count) throw new IllegalArgumentException("Invalid tenancy policy row: " + line);
        return parts;
    }

    private static void validateClassification(String[] parts) {
        if (!Set.of("tenant", "global", "business").contains(parts[1]) || parts[2].isBlank()) {
            throw new IllegalArgumentException("Port classifications need a known category and reason");
        }
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }
}
