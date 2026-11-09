package com.edispark.transformations.application;

import com.edispark.transformations.domain.MappingNode;
import com.edispark.transformations.domain.MappingOperation;
import com.edispark.transformations.domain.MappingRevision;
import com.edispark.transformations.domain.MappingWarning;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;

@Service
public final class MappingRuntime {
    private static final int MAX_INPUT_BYTES = 25 * 1024 * 1024;
    private static final int MAX_OUTPUT_BYTES = 25 * 1024 * 1024;
    private final ObjectMapper mapper;

    public MappingRuntime(ObjectMapper mapper) { this.mapper = mapper; }

    public byte[] execute(MappingRevision mapping, byte[] input) {
        return executeDetailed(mapping, input).output();
    }

    public ExecutionResult executeDetailed(MappingRevision mapping, byte[] input) {
        if (input.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("Mapping input exceeds 25 MiB");
        try {
            JsonNode source = mapper.readTree(input);
            ObjectNode target = mapper.createObjectNode();
            List<String> consumed = new ArrayList<>();
            for (MappingNode node : mapping.nodes()) apply(source, target, node, consumed);
            byte[] output = mapper.writeValueAsBytes(target);
            if (output.length > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("Mapping output exceeds 25 MiB");
            List<MappingWarning> warnings = unmappedWarnings(source, consumed);
            ObjectNode report = mapper.createObjectNode();
            report.put("mappingRevisionId", mapping.id().toString());
            report.put("mappingFamilyId", mapping.familyId().toString());
            report.put("mappingRevision", mapping.revision());
            report.put("mappingHash", mapping.contentHash());
            report.set("reviewedOmissions", mapper.valueToTree(mapping.nodes().stream().filter(node -> node.operation() == MappingOperation.OMIT)
                    .map(node -> java.util.Map.of("paths", node.sourcePointers(), "reason", node.constant())).toList()));
            report.put("mappingName", mapping.name());
            report.put("sourceSchemaId", mapping.sourceSchemaId().toString());
            report.put("targetSchemaId", mapping.targetSchemaId().toString());
            report.set("warnings", mapper.valueToTree(warnings));
            return new ExecutionResult(output, mapper.writeValueAsBytes(report), warnings);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Mapping execution failed", exception);
        }
    }

    private List<MappingWarning> unmappedWarnings(JsonNode source, List<String> mapped) {
        Set<String> leaves = new LinkedHashSet<>();
        collectLeaves(source, "", leaves);
        return leaves.stream()
                .filter(path -> !path.equals("/_D") && !path.equals("/_A") && !path.equals("/_B"))
                .filter(path -> mapped.stream().noneMatch(pointer -> covers(pointer, path)))
                .map(path -> new MappingWarning("SOURCE_FIELD_UNMAPPED", path, "SOURCE_ONLY",
                        "The populated source field has no mapping in this revision."))
                .toList();
    }

    private boolean covers(String pointer, String path) {
        String[] pattern = pointer.split("/"), actual = path.split("/");
        if (pattern.length > actual.length) return false;
        for (int index = 0; index < pattern.length; index++) {
            if (pattern[index].equals("*")) { if (!actual[index].matches("\\d+")) return false; }
            else if (!pattern[index].equals(actual[index])) return false;
        }
        return true;
    }

    private void apply(JsonNode source, ObjectNode target, MappingNode node, List<String> consumed) {
        if (node.operation() == MappingOperation.OMIT) { consumed.addAll(node.sourcePointers()); return; }
        String repeated = node.sourcePointers().stream().filter(pointer -> pointer.contains("/*/")).findFirst().orElse(null);
        if (repeated == null) {
            if (node.targetPointer().contains("/*/")) throw new IllegalArgumentException("A repeated target requires an aligned source collection");
            if (optional(node) && node.sourcePointers().stream().allMatch(pointer -> source.at(pointer).isMissingNode() || source.at(pointer).isNull())) return;
            write(target, node.targetPointer(), value(source, node));
            if (node.operation() != MappingOperation.CONSTANT) {
                var available = node.sourcePointers().stream().filter(pointer -> !source.at(pointer).isMissingNode() && !source.at(pointer).isNull());
                consumed.addAll(node.operation() == MappingOperation.CONCAT ? available.toList() : available.limit(1).toList());
            }
            return;
        }
        if (repeated.indexOf("/*/") != repeated.lastIndexOf("/*/") || !node.targetPointer().contains("/*/")) {
            throw new IllegalArgumentException("Repeated mapping requires one aligned source and target collection");
        }
        JsonNode collection = source.at(repeated.substring(0, repeated.indexOf("/*/")));
        if (!collection.isArray()) {
            if (optional(node)) return;
            throw new IllegalArgumentException("The mapping's repeated source collection is missing");
        }
        if (collection.size() > 10000) throw new IllegalArgumentException("Repeated mapping exceeds 10000 items");
        for (int index = 0; index < collection.size(); index++) {
            String replacement = "/" + index + "/";
            MappingNode expanded = new MappingNode(node.id(), node.operation(), node.sourcePointers().stream()
                    .map(pointer -> pointer.replace("/*/", replacement)).toList(), node.targetPointer().replace("/*/", replacement),
                    node.constant(), node.separator(), node.codeMap(), node.codeList(), node.sourceSyntax(), node.targetSyntax());
            apply(source, target, expanded, consumed);
        }
    }

    private boolean optional(MappingNode node) { return node.operation() == MappingOperation.OPTIONAL_COPY || node.operation() == MappingOperation.OPTIONAL_CODE_LOOKUP; }

    private void collectLeaves(JsonNode value, String path, Set<String> output) {
        if (value == null || value.isNull() || value.isMissingNode()) return;
        if (value.isValueNode()) {
            if (!value.isTextual() || !value.asText().isBlank()) output.add(path.isEmpty() ? "/" : path);
            return;
        }
        if (value.isArray()) {
            for (int index = 0; index < value.size(); index++) collectLeaves(value.get(index), path + "/" + index, output);
            return;
        }
        value.fields().forEachRemaining(entry -> collectLeaves(entry.getValue(), path + "/" + escape(entry.getKey()), output));
    }

    private String escape(String value) { return value.replace("~", "~0").replace("/", "~1"); }

    public record ExecutionResult(byte[] output, byte[] report, List<MappingWarning> warnings) {
        public ExecutionResult {
            output = output.clone();
            report = report.clone();
            warnings = List.copyOf(warnings);
        }
        @Override public byte[] output() { return output.clone(); }
        @Override public byte[] report() { return report.clone(); }
    }

    private JsonNode value(JsonNode source, MappingNode node) {
        if (node.operation() == MappingOperation.CONSTANT) return mapper.getNodeFactory().textNode(node.constant());
        List<JsonNode> values = new ArrayList<>();
        for (String pointer : node.sourcePointers()) {
            JsonNode value = source.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) values.add(value);
        }
        return switch (node.operation()) {
            case COPY, OPTIONAL_COPY -> first(values, node);
            case COALESCE -> first(values, node);
            case CONCAT -> mapper.getNodeFactory().textNode(values.stream().map(JsonNode::asText)
                    .collect(java.util.stream.Collectors.joining(node.separator() == null ? "" : node.separator())));
            case UPPERCASE -> mapper.getNodeFactory().textNode(first(values, node).asText().toUpperCase(java.util.Locale.ROOT));
            case LOWERCASE -> mapper.getNodeFactory().textNode(first(values, node).asText().toLowerCase(java.util.Locale.ROOT));
            case CODE_LOOKUP, OPTIONAL_CODE_LOOKUP -> codeLookup(values, node);
            case CONSTANT -> throw new IllegalStateException("Handled above");
            case OMIT -> throw new IllegalStateException("Omissions do not emit target values");
        };
    }

    private JsonNode codeLookup(List<JsonNode> values, MappingNode node) {
        String sourceCode = first(values, node).asText();
        String targetCode = node.codeMap().get(sourceCode);
        if (targetCode == null) {
            throw new IllegalArgumentException("No code mapping for " + sourceCode + " in mapping node " + node.id());
        }
        return mapper.getNodeFactory().textNode(targetCode);
    }

    private JsonNode first(List<JsonNode> values, MappingNode node) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Mapping cannot populate target '" + node.targetPointer()
                    + "' because none of the configured source fields contain a value: " + node.sourcePointers()
                    + ". Add the missing value to the source document or update mapping node " + node.id()
                    + " (" + node.operation() + ") to use an available source field.");
        }
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
            if (index > 10000) throw new IllegalArgumentException("Mapping target array exceeds 10000 items");
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
