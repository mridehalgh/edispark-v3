package com.example.documents.infrastructure.persistence;

import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.ContentRef;
import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaFormat;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersion;
import com.example.documents.domain.model.SchemaVersionId;
import com.example.documents.domain.model.VersionIdentifier;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.documents.infrastructure.persistence.DynamoDbTableConfig.*;

/**
 * Maps Schema aggregate and its entities to/from DynamoDB items.
 */
public final class SchemaItemMapper {

    private final String tenantId;

    public SchemaItemMapper() {
        this(DEFAULT_TENANT);
    }

    public SchemaItemMapper(String tenantId) {
        this.tenantId = tenantId;
    }

    // ========== Schema Metadata ==========

    /**
     * Creates a DynamoDB item for Schema metadata.
     */
    public Map<String, AttributeValue> toSchemaMetadataItem(Schema schema) {
        Map<String, AttributeValue> item = new HashMap<>();
        
        String schemaId = schema.id().toString();
        item.put(PK, AttributeValue.builder().s(schemaPk(tenantId, schemaId)).build());
        item.put(SK, AttributeValue.builder().s(schemaMetadataSk()).build());
        
        item.put("schemaId", AttributeValue.builder().s(schemaId).build());
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("name", AttributeValue.builder().s(schema.name()).build());
        item.put("schemaFormat", AttributeValue.builder().s(schema.format().name()).build());
        
        item.put("entityType", AttributeValue.builder().s("SCHEMA").build());
        
        return item;
    }

    /**
     * Extracts Schema metadata from a DynamoDB item.
     */
    public SchemaMetadata fromSchemaMetadataItem(Map<String, AttributeValue> item) {
        SchemaId id = SchemaId.fromString(item.get("schemaId").s());
        String name = item.get("name").s();
        SchemaFormat format = SchemaFormat.valueOf(item.get("schemaFormat").s());
        
        return new SchemaMetadata(id, name, format);
    }

    // ========== SchemaVersion ==========

    /**
     * Creates a DynamoDB item for a SchemaVersion.
     */
    public Map<String, AttributeValue> toSchemaVersionItem(SchemaId schemaId, SchemaVersion version) {
        Map<String, AttributeValue> item = new HashMap<>();
        
        String schemaIdStr = schemaId.toString();
        String versionId = version.versionIdentifier().value();
        
        item.put(PK, AttributeValue.builder().s(schemaPk(tenantId, schemaIdStr)).build());
        item.put(SK, AttributeValue.builder().s(schemaVersionSk(versionId)).build());
        
        item.put("schemaVersionId", AttributeValue.builder().s(version.id().toString()).build());
        item.put("schemaId", AttributeValue.builder().s(schemaIdStr).build());
        item.put("versionIdentifier", AttributeValue.builder().s(versionId).build());
        item.put("contentHash", AttributeValue.builder().s(version.definitionRef().hash().toFullString()).build());
        item.put("contentHashAlgorithm", AttributeValue.builder().s(version.definitionRef().hash().algorithm()).build());
        item.put("contentHashValue", AttributeValue.builder().s(version.definitionRef().hash().hash()).build());
        item.put("createdAt", AttributeValue.builder().s(version.createdAt().toString()).build());
        item.put("deprecated", AttributeValue.builder().bool(version.isDeprecated()).build());
        
        item.put("entityType", AttributeValue.builder().s("SCHEMA_VERSION").build());
        
        return item;
    }

    /**
     * Extracts SchemaVersion from a DynamoDB item.
     */
    public SchemaVersion fromSchemaVersionItem(Map<String, AttributeValue> item) {
        SchemaVersionId id = SchemaVersionId.fromString(item.get("schemaVersionId").s());
        VersionIdentifier versionIdentifier = VersionIdentifier.of(item.get("versionIdentifier").s());
        
        String algorithm = item.get("contentHashAlgorithm").s();
        String hashValue = item.get("contentHashValue").s();
        ContentHash contentHash = new ContentHash(algorithm, hashValue);
        ContentRef definitionRef = ContentRef.of(contentHash);
        
        Instant createdAt = Instant.parse(item.get("createdAt").s());
        boolean deprecated = item.containsKey("deprecated") && item.get("deprecated").bool();
        
        return SchemaVersion.reconstitute(id, versionIdentifier, definitionRef, createdAt, deprecated);
    }

    // ========== Aggregate Reconstruction ==========

    /**
     * Reconstructs a Schema aggregate from DynamoDB items.
     */
    public Schema reconstructSchema(SchemaMetadata metadata, List<SchemaVersion> versions) {
        return Schema.reconstitute(
                metadata.id(),
                metadata.name(),
                metadata.format(),
                new ArrayList<>(versions));
    }

    /**
     * Generates all DynamoDB items for a Schema aggregate.
     */
    public List<Map<String, AttributeValue>> toItems(Schema schema) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        
        // Schema metadata
        items.add(toSchemaMetadataItem(schema));
        
        // Schema versions
        for (SchemaVersion version : schema.versions()) {
            items.add(toSchemaVersionItem(schema.id(), version));
        }
        
        return items;
    }

    // ========== Helper Records ==========

    public record SchemaMetadata(
            SchemaId id,
            String name,
            SchemaFormat format) {}
}
