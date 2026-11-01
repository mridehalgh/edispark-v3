package com.edispark.transformations.application;

import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.MappingRevision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class MappingRuntime {
    private static final int MAX_INPUT_BYTES = 25 * 1024 * 1024;
    private static final int MAX_OUTPUT_BYTES = 25 * 1024 * 1024;
    private final ObjectMapper mapper;

    public MappingRuntime(ObjectMapper mapper) { this.mapper = mapper; }

    public byte[] execute(MappingRevision mapping, byte[] input) {
        if (input.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("Mapping input exceeds 25 MiB");
        try {
            JsonNode source = mapper.readTree(input);
            ObjectNode target = mapper.createObjectNode();
            for (MappingNode node : mapping.nodes()) write(target, node.targetPointer(), value(source, node));
            byte[] output = mapper.writeValueAsBytes(target);
            if (output.length > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("Mapping output exceeds 25 MiB");
            return output;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Mapping execution failed", exception);
        }
    }

    private JsonNode value(JsonNode source, MappingNode node) {
        if (node.operation() == MappingOperation.CONSTANT) return mapper.getNodeFactory().textNode(node.constant());
        List<JsonNode> values = new ArrayList<>();
        for (String pointer : node.sourcePointers()) {
            JsonNode value = source.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) values.add(value);
        }
        return switch (node.operation()) {
            case COPY -> first(values, node);
            case COALESCE -> first(values, node);
            case CONCAT -> mapper.getNodeFactory().textNode(values.stream().map(JsonNode::asText)
                    .collect(java.util.stream.Collectors.joining(node.separator() == null ? "" : node.separator())));
            case UPPERCASE -> mapper.getNodeFactory().textNode(first(values, node).asText().toUpperCase(java.util.Locale.ROOT));
            case LOWERCASE -> mapper.getNodeFactory().textNode(first(values, node).asText().toLowerCase(java.util.Locale.ROOT));
            case CONSTANT -> throw new IllegalStateException("Handled above");
        };
    }

    private JsonNode first(List<JsonNode> values, MappingNode node) {
        if (values.isEmpty()) throw new IllegalArgumentException("No source value for mapping node " + node.id());
        return values.getFirst().deepCopy();
    }

    private void write(ObjectNode root, String pointer, JsonNode value) {
        String[] parts = pointer.substring(1).split("/");
        JsonNode cursor = root;
        for (int index = 0; index < parts.length - 1; index++) {
            String part = unescape(parts[index]);
            boolean arrayNext = numeric(parts[index + 1]);
            JsonNode child = child(cursor, part);
            if (child == null || child.isNull()) {
                child = arrayNext ? mapper.createArrayNode() : mapper.createObjectNode();
                set(cursor, part, child);
            }
            cursor = child;
        }
        set(cursor, unescape(parts[parts.length - 1]), value);
    }

    private JsonNode child(JsonNode parent, String part) {
        if (parent instanceof ObjectNode object) return object.get(part);
        if (parent instanceof ArrayNode array && numeric(part)) {
            int index = Integer.parseInt(part);
            return index < array.size() ? array.get(index) : null;
        }
        throw new IllegalArgumentException("Mapping target path crosses an incompatible JSON container");
    }

    private void set(JsonNode parent, String part, JsonNode value) {
        if (parent instanceof ObjectNode object) { object.set(part, value); return; }
        if (parent instanceof ArrayNode array && numeric(part)) {
            int index = Integer.parseInt(part);
            while (array.size() <= index) array.add(NullNode.getInstance());
            array.set(index, value);
            return;
        }
        throw new IllegalArgumentException("Mapping target path crosses an incompatible JSON container");
    }

    private boolean numeric(String value) { return value.chars().allMatch(Character::isDigit); }

    private String unescape(String value) { return value.replace("~1", "/").replace("~0", "~"); }
}
