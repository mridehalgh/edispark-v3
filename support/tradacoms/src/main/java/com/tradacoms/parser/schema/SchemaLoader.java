package com.tradacoms.parser.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads TRADACOMS message schemas from JSON files.
 * Supports the standard TRADACOMS JSON schema format (e.g., CREDIT.json).
 */
public final class SchemaLoader {

    private final ObjectMapper objectMapper;

    public SchemaLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Load a message schema from a JSON file path.
     * 
     * @param jsonFile path to the JSON schema file
     * @return list of message schemas defined in the file
     * @throws IOException if the file cannot be read or parsed
     */
    public List<MessageSchema> loadFromJson(Path jsonFile) throws IOException {
        try (InputStream is = Files.newInputStream(jsonFile)) {
            return loadFromJson(is);
        }
    }

    /**
     * Load a message schema from an input stream.
     * 
     * @param input the input stream containing JSON schema
     * @return list of message schemas defined in the JSON
     * @throws IOException if the stream cannot be read or parsed
     */
    public List<MessageSchema> loadFromJson(InputStream input) throws IOException {
        JsonNode root = objectMapper.readTree(input);
        return parseSchemaRoot(root);
    }

    /**
     * Load a message schema from a JSON string.
     * 
     * @param json the JSON string containing schema definition
     * @return list of message schemas defined in the JSON
     * @throws IOException if the JSON cannot be parsed
     */
    public List<MessageSchema> loadFromJson(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        return parseSchemaRoot(root);
    }

    /**
     * Load all schemas from a directory of JSON files.
     * 
     * @param dir the directory containing JSON schema files
     * @return map of message type ID to schema
     * @throws IOException if the directory cannot be read
     */
    public Map<String, MessageSchema> loadAllFromDirectory(Path dir) throws IOException {
        Map<String, MessageSchema> schemas = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .forEach(p -> {
                     try {
                         List<MessageSchema> loaded = loadFromJson(p);
                         for (MessageSchema schema : loaded) {
                             schemas.put(schema.getId(), schema);
                         }
                     } catch (IOException e) {
                         throw new RuntimeException("Failed to load schema from " + p, e);
                     }
                 });
        }
        return schemas;
    }

    private List<MessageSchema> parseSchemaRoot(JsonNode root) {
        List<MessageSchema> schemas = new ArrayList<>();
        
        JsonNode messagesNode = root.get("messages");
        if (messagesNode != null && messagesNode.isArray()) {
            for (JsonNode messageNode : messagesNode) {
                schemas.add(parseMessageSchema(messageNode));
            }
        }
        
        return schemas;
    }

    private MessageSchema parseMessageSchema(JsonNode node) {
        String id = getTextOrNull(node, "id");
        String messageClass = getTextOrNull(node, "messageClass");
        String name = getTextOrNull(node, "name");
        
        Map<String, SegmentOrGroupSchema> segments = new LinkedHashMap<>();
        JsonNode segmentsNode = node.get("segments");
        if (segmentsNode != null && segmentsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = segmentsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode segmentNode = entry.getValue();
                segments.put(key, parseSegmentOrGroup(segmentNode));
            }
        }
        
        return new MessageSchema(id, messageClass, name, segments);
    }

    private SegmentOrGroupSchema parseSegmentOrGroup(JsonNode node) {
        // Check if this is a group (has groupId property)
        if (node.has("groupId")) {
            return parseGroupSchema(node);
        }
        return parseSegmentSchema(node);
    }

    private GroupSchema parseGroupSchema(JsonNode node) {
        String groupId = getTextOrNull(node, "groupId");
        String name = getTextOrNull(node, "name");
        String slug = getTextOrNull(node, "slug");
        String usage = getTextOrNull(node, "usage");
        String count = getTextOrNull(node, "count");
        
        Map<String, SegmentOrGroupSchema> segments = new LinkedHashMap<>();
        JsonNode segmentsNode = node.get("segments");
        if (segmentsNode != null && segmentsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = segmentsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode segmentNode = entry.getValue();
                segments.put(key, parseSegmentOrGroup(segmentNode));
            }
        }
        
        return new GroupSchema(groupId, name, slug, usage, count, segments);
    }

    private SegmentSchema parseSegmentSchema(JsonNode node) {
        String id = getTextOrNull(node, "id");
        String name = getTextOrNull(node, "name");
        String slug = getTextOrNull(node, "slug");
        String position = getTextOrNull(node, "position");
        String usage = getTextOrNull(node, "usage");
        String count = getTextOrNull(node, "count");
        
        Map<String, ElementSchema> values = new LinkedHashMap<>();
        JsonNode valuesNode = node.get("values");
        if (valuesNode != null && valuesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = valuesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode elementNode = entry.getValue();
                values.put(key, parseElementSchema(elementNode));
            }
        }
        
        return new SegmentSchema(id, name, slug, position, usage, count, values);
    }

    private ElementSchema parseElementSchema(JsonNode node) {
        String id = getTextOrNull(node, "id");
        String name = getTextOrNull(node, "name");
        String slug = getTextOrNull(node, "slug");
        String usage = getTextOrNull(node, "usage");
        String type = getTextOrNull(node, "type");
        Integer length = getIntegerOrNull(node, "length");
        Integer minLength = getIntegerOrNull(node, "minLength");
        Integer maxLength = getIntegerOrNull(node, "maxLength");
        
        List<ComponentSchema> components = new ArrayList<>();
        JsonNode valuesNode = node.get("values");
        if (valuesNode != null && valuesNode.isArray()) {
            for (JsonNode componentNode : valuesNode) {
                components.add(parseComponentSchema(componentNode));
            }
        }
        
        return new ElementSchema(id, name, slug, usage, type, length, minLength, maxLength, components);
    }

    private ComponentSchema parseComponentSchema(JsonNode node) {
        String name = getTextOrNull(node, "name");
        String slug = getTextOrNull(node, "slug");
        String usage = getTextOrNull(node, "usage");
        String type = getTextOrNull(node, "type");
        Integer length = getIntegerOrNull(node, "length");
        Integer minLength = getIntegerOrNull(node, "minLength");
        Integer maxLength = getIntegerOrNull(node, "maxLength");
        
        return new ComponentSchema(name, slug, usage, type, length, minLength, maxLength);
    }

    private String getTextOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return field.asText();
    }

    private Integer getIntegerOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return field.asInt();
    }
}
