package com.edispark.documents.application.handler;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.Format;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Keep retained fields that the business form does not expose when producing a correction. */
final class CorrectionContent {
    private CorrectionContent() {}

    static Content merge(byte[] source, Content edited) {
        if (edited.format() != Format.JSON) throw new IllegalArgumentException("Corrections require canonical UBL JSON");
        try {
            var json = new ObjectMapper();
            JsonNode result = mergeNode(json.readTree(source), json.readTree(edited.data()));
            if (result.path("OrderResponse").path(0) instanceof ObjectNode response && response.has("OrderResponseCode")) {
                response.remove("ResponseCode"); // Repair the legacy authoring field; the original bytes stay immutable.
                if (response.has("Description") && !response.has("Note")) response.set("Note", response.get("Description"));
                response.remove("Description");
            }
            return Content.of(json.writeValueAsBytes(result), Format.JSON);
        } catch (java.io.IOException exception) { throw new IllegalArgumentException("The correction source is not readable JSON"); }
    }

    private static JsonNode mergeNode(JsonNode source, JsonNode edited) {
        if (source != null && source.isObject() && edited.isObject()) {
            ObjectNode result = ((ObjectNode) source).deepCopy();
            edited.fields().forEachRemaining(entry -> result.set(entry.getKey(), mergeNode(result.get(entry.getKey()), entry.getValue())));
            return result;
        }
        if (source != null && source.isArray() && edited.isArray()) {
            var result = ((com.fasterxml.jackson.databind.node.ArrayNode) edited).deepCopy();
            for (int index = 0; index < result.size(); index++) {
                JsonNode previous = source.path(index);
                if (source.size() > 1 || result.size() > 1) {
                    String id = identity(result.get(index));
                    if (id != null) {
                        previous = null;
                        for (JsonNode candidate : source) if (id.equals(identity(candidate))) { previous = candidate; break; }
                    }
                }
                result.set(index, mergeNode(previous, result.get(index)));
            }
            return result;
        }
        return edited.deepCopy();
    }

    private static String identity(JsonNode value) {
        JsonNode line = value.path("LineItem").path(0);
        String id = (line.isMissingNode() ? value : line).path("ID").path(0).path("_").asText("");
        return id.isBlank() ? null : id;
    }
}
