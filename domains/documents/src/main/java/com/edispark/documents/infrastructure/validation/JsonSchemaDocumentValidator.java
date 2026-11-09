package com.edispark.documents.infrastructure.validation;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.Format;
import com.edispark.documents.domain.model.SchemaFormat;
import com.edispark.documents.domain.model.ValidationError;
import com.edispark.documents.domain.model.ValidationResult;
import com.edispark.documents.domain.service.DocumentValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Offline schema validation. Tenant schemas cannot cause network or filesystem reads. */
public final class JsonSchemaDocumentValidator implements DocumentValidator {
    private static final List<String> ROOTS = List.of("Order", "Invoice", "CreditNote", "OrderResponse", "DespatchAdvice", "ReceiptAdvice", "RemittanceAdvice");
    private final ObjectMapper json = new ObjectMapper();
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    // UBL's aggregate graph is recursive: resolve only the definitions reached by this document.
    private final SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().preloadJsonSchema(false)
            .formatAssertionsEnabled(true).pathType(com.networknt.schema.PathType.JSON_POINTER).build();
    private final Map<String, JsonSchema> bundled = new ConcurrentHashMap<>();

    @Override public boolean supports(Format format, SchemaFormat schema) { return format == Format.JSON && schema == SchemaFormat.JSON_SCHEMA; }

    @Override public ValidationResult validate(Content document, Content schema) {
        try {
            JsonNode definition = json.readTree(schema.data());
            String dialect = definition.path("$schema").asText("");
            if (!dialect.isEmpty() && !Set.of("http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft-07/schema",
                    "https://json-schema.org/draft/2020-12/schema", "https://json-schema.org/draft/2019-09/schema").contains(dialect)) {
                return failure("/", "This schema dialect is not supported for offline validation.", "VALIDATION_UNAVAILABLE");
            }
            if (unsafeReferences(definition)) return failure("/", "External schema references are not permitted.", "VALIDATION_UNAVAILABLE");
            JsonNode input = json.readTree(document.data());
            List<ValidationError> errors = new ArrayList<>();
            addErrors(factory.getSchema(definition, config), input, errors);
            String root = ROOTS.stream().filter(name -> definition.path("properties").has(name)).findFirst().orElse(null);
            if (root == null) root = ROOTS.stream().filter(input::has).findFirst().orElse(null);
            if (root != null) {
                if (!input.path("_D").asText().equals("urn:oasis:names:specification:ubl:schema:xsd:" + root + "-2")) {
                    errors.add(ValidationError.of("/_D", "Choose the correct UBL document namespace.", "UBL_NAMESPACE_INVALID"));
                }
                addErrors(bundled.computeIfAbsent(root, name -> factory.getSchema(SchemaLocation.of(
                        "classpath:ubl/2.1/json-schema/maindoc/UBL-" + name + "-2.1.json"), config)), input, errors);
            }
            return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors.stream().distinct().limit(200).toList());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return failure("/", "The document or schema is not readable JSON.", "INVALID_JSON");
        } catch (Exception exception) {
            return failure("/", "Validation could not be completed. Ask an administrator to review the schema configuration.", "VALIDATION_UNAVAILABLE");
        }
    }

    public ValidationResult validateUbl(byte[] bytes, String root) {
        if (!ROOTS.contains(root)) return failure("/", "Validation is unavailable for this document type.", "VALIDATION_UNAVAILABLE");
        String schema = "{\"type\":\"object\",\"required\":[\"" + root + "\"],\"properties\":{\"" + root + "\":{}}}";
        return validate(Content.of(bytes, Format.JSON), Content.of(schema.getBytes(java.nio.charset.StandardCharsets.UTF_8), Format.JSON));
    }

    private void addErrors(JsonSchema schema, JsonNode input, List<ValidationError> errors) {
        schema.validate(input).stream().limit(200).forEach(issue -> errors.add(ValidationError.of(
                issue.getInstanceLocation().toString(), "This field does not satisfy the " + issue.getType() + " schema rule"
                        + (issue.getProperty() == null ? "." : " (" + issue.getProperty() + ")."), "SCHEMA_" + issue.getType().toUpperCase(Locale.ROOT))));
    }

    private boolean unsafeReferences(JsonNode node) {
        if (node.isObject()) {
            for (String key : List.of("$ref", "$dynamicRef", "$recursiveRef", "$id")) {
                if (node.has(key) && !node.path(key).asText().startsWith("#") && !key.equals("$id")) return true;
            }
        }
        for (JsonNode child : node) if (unsafeReferences(child)) return true;
        return false;
    }

    private ValidationResult failure(String path, String message, String code) { return ValidationResult.failure(List.of(ValidationError.of(path, message, code))); }
}
