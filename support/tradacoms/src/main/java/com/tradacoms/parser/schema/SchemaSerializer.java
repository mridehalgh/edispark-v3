package com.tradacoms.parser.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Serializes TRADACOMS message schemas to JSON format.
 * Produces output compatible with the standard TRADACOMS JSON schema format.
 */
public final class SchemaSerializer {

    private final ObjectMapper objectMapper;

    public SchemaSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Serialize message schemas to a JSON file.
     * 
     * @param schemas the schemas to serialize
     * @param outputFile the output file path
     * @throws IOException if the file cannot be written
     */
    public void writeToJson(List<MessageSchema> schemas, Path outputFile) throws IOException {
        try (OutputStream os = Files.newOutputStream(outputFile)) {
            writeToJson(schemas, os);
        }
    }

    /**
     * Serialize message schemas to an output stream.
     * 
     * @param schemas the schemas to serialize
     * @param output the output stream
     * @throws IOException if writing fails
     */
    public void writeToJson(List<MessageSchema> schemas, OutputStream output) throws IOException {
        ObjectNode root = serializeSchemas(schemas);
        objectMapper.writeValue(output, root);
    }

    /**
     * Serialize message schemas to a JSON string.
     * 
     * @param schemas the schemas to serialize
     * @return the JSON string
     * @throws IOException if serialization fails
     */
    public String writeToJsonString(List<MessageSchema> schemas) throws IOException {
        ObjectNode root = serializeSchemas(schemas);
        StringWriter writer = new StringWriter();
        objectMapper.writeValue(writer, root);
        return writer.toString();
    }

    private ObjectNode serializeSchemas(List<MessageSchema> schemas) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode messagesArray = objectMapper.createArrayNode();
        
        for (MessageSchema schema : schemas) {
            messagesArray.add(serializeMessageSchema(schema));
        }
        
        root.set("messages", messagesArray);
        return root;
    }

    private ObjectNode serializeMessageSchema(MessageSchema schema) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", schema.getId());
        putIfNotNull(node, "messageClass", schema.getMessageClass());
        putIfNotNull(node, "name", schema.getName());
        
        ObjectNode segmentsNode = objectMapper.createObjectNode();
        for (Map.Entry<String, SegmentOrGroupSchema> entry : schema.getSegments().entrySet()) {
            segmentsNode.set(entry.getKey(), serializeSegmentOrGroup(entry.getValue()));
        }
        node.set("segments", segmentsNode);
        
        return node;
    }

    private ObjectNode serializeSegmentOrGroup(SegmentOrGroupSchema schema) {
        if (schema instanceof GroupSchema groupSchema) {
            return serializeGroupSchema(groupSchema);
        } else if (schema instanceof SegmentSchema segmentSchema) {
            return serializeSegmentSchema(segmentSchema);
        }
        throw new IllegalArgumentException("Unknown schema type: " + schema.getClass());
    }

    private ObjectNode serializeGroupSchema(GroupSchema schema) {
        ObjectNode node = objectMapper.createObjectNode();
        putIfNotNull(node, "name", schema.getName());
        putIfNotNull(node, "slug", schema.getSlug());
        node.putNull("position");
        putIfNotNull(node, "usage", schema.getUsage());
        putIfNotNull(node, "count", schema.getCount());
        node.put("groupId", schema.getGroupId());
        
        ObjectNode segmentsNode = objectMapper.createObjectNode();
        for (Map.Entry<String, SegmentOrGroupSchema> entry : schema.getSegments().entrySet()) {
            segmentsNode.set(entry.getKey(), serializeSegmentOrGroup(entry.getValue()));
        }
        node.set("segments", segmentsNode);
        
        return node;
    }

    private ObjectNode serializeSegmentSchema(SegmentSchema schema) {
        ObjectNode node = objectMapper.createObjectNode();
        putIfNotNull(node, "name", schema.getName());
        putIfNotNull(node, "slug", schema.getSlug());
        putIfNotNull(node, "position", schema.getPosition());
        putIfNotNull(node, "usage", schema.getUsage());
        putIfNotNull(node, "count", schema.getCount());
        putIfNotNull(node, "id", schema.getId());
        
        ObjectNode valuesNode = objectMapper.createObjectNode();
        for (Map.Entry<String, ElementSchema> entry : schema.getValues().entrySet()) {
            valuesNode.set(entry.getKey(), serializeElementSchema(entry.getValue()));
        }
        node.set("values", valuesNode);
        node.put("type", "SEGMENT");
        
        return node;
    }

    private ObjectNode serializeElementSchema(ElementSchema schema) {
        ObjectNode node = objectMapper.createObjectNode();
        putIfNotNull(node, "id", schema.getId());
        putIfNotNull(node, "slug", schema.getSlug());
        putIfNotNull(node, "name", schema.getName());
        putIfNotNull(node, "usage", schema.getUsage());
        
        ArrayNode valuesArray = objectMapper.createArrayNode();
        for (ComponentSchema component : schema.getValues()) {
            valuesArray.add(serializeComponentSchema(component));
        }
        node.set("values", valuesArray);
        
        putIfNotNull(node, "type", schema.getType());
        putIntegerOrNull(node, "minLength", schema.getMinLength());
        putIntegerOrNull(node, "maxLength", schema.getMaxLength());
        putIntegerOrNull(node, "length", schema.getLength());
        
        return node;
    }

    private ObjectNode serializeComponentSchema(ComponentSchema schema) {
        ObjectNode node = objectMapper.createObjectNode();
        putIfNotNull(node, "name", schema.getName());
        putIfNotNull(node, "slug", schema.getSlug());
        putIfNotNull(node, "usage", schema.getUsage());
        putIfNotNull(node, "type", schema.getType());
        putIntegerOrNull(node, "length", schema.getLength());
        putIntegerOrNull(node, "minLength", schema.getMinLength());
        putIntegerOrNull(node, "maxLength", schema.getMaxLength());
        node.set("values", objectMapper.createArrayNode());
        
        return node;
    }

    private void putIfNotNull(ObjectNode node, String fieldName, String value) {
        if (value != null) {
            node.put(fieldName, value);
        } else {
            node.putNull(fieldName);
        }
    }

    private void putIntegerOrNull(ObjectNode node, String fieldName, Integer value) {
        if (value != null) {
            node.put(fieldName, value);
        } else {
            node.putNull(fieldName);
        }
    }
}
