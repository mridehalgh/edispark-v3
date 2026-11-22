package com.edispark.edifact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Positional segment and group declarations from the UN/EDIFACT message directory. */
public record EdifactMessageStructure(String source, String digest, List<Entry> entries) {
    public record Entry(String position, String area, String groupPath, String tag, String usage, int maxRepeats) { }

    public static Optional<EdifactMessageStructure> find(String release, String message) {
        return "D96A".equals(release) && "ORDERS".equals(message) ? Optional.of(D96aOrders.STRUCTURE) : Optional.empty();
    }

    private static final class D96aOrders {
        private static final EdifactMessageStructure STRUCTURE = read("/edifact/d96a/orders.tsv",
                "https://service.unece.org/trade/untdid/d96a/trmd/orders_s.htm");
    }

    private static EdifactMessageStructure read(String path, String source) {
        try (var stream = Objects.requireNonNull(EdifactMessageStructure.class.getResourceAsStream(path), path)) {
            byte[] bytes = stream.readAllBytes();
            var entries = new String(bytes, StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.startsWith("#")).map(EdifactMessageStructure::entry).toList();
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new EdifactMessageStructure(source, "sha-256:" + digest, entries);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Entry entry(String line) {
        String[] fields = line.split("\t");
        return new Entry(fields[0], fields[1], fields[2], fields[3], fields[4], Integer.parseInt(fields[5]));
    }
}
