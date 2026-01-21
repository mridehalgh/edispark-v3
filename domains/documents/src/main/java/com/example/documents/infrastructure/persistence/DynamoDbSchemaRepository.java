package com.example.documents.infrastructure.persistence;

import com.example.documents.domain.model.Schema;
import com.example.documents.domain.model.SchemaId;
import com.example.documents.domain.model.SchemaVersion;
import com.example.documents.domain.model.SchemaVersionRef;
import com.example.documents.domain.repository.SchemaRepository;
import com.example.documents.infrastructure.persistence.SchemaItemMapper.SchemaMetadata;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.documents.infrastructure.persistence.DynamoDbTableConfig.*;

/**
 * DynamoDB implementation of SchemaRepository.
 * 
 * <p>This repository uses a single-table design where Schema metadata and
 * SchemaVersions are stored in the same table with different sort key patterns.</p>
 * 
 * <p>Requirements: 3.6</p>
 */
public class DynamoDbSchemaRepository implements SchemaRepository {

    private final DynamoDbClient client;
    private final String tableName;
    private final String tenantId;
    private final SchemaItemMapper mapper;

    public DynamoDbSchemaRepository(DynamoDbClient client, String tableName) {
        this(client, tableName, DEFAULT_TENANT);
    }

    public DynamoDbSchemaRepository(DynamoDbClient client, String tableName, String tenantId) {
        this.client = client;
        this.tableName = tableName;
        this.tenantId = tenantId;
        this.mapper = new SchemaItemMapper(tenantId);
    }

    @Override
    public Optional<Schema> findById(SchemaId id) {
        String pk = schemaPk(tenantId, id.toString());
        
        // Query all items for this Schema (metadata + versions)
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", PK))
                .expressionAttributeValues(Map.of(":pk", AttributeValue.builder().s(pk).build()))
                .build();
        
        QueryResponse response = client.query(request);
        
        if (response.items().isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.of(reconstructFromItems(response.items()));
    }

    @Override
    public void save(Schema schema) {
        List<Map<String, AttributeValue>> items = mapper.toItems(schema);
        
        // Batch write all items (DynamoDB allows max 25 items per batch)
        List<WriteRequest> writeRequests = items.stream()
                .map(item -> WriteRequest.builder()
                        .putRequest(PutRequest.builder().item(item).build())
                        .build())
                .toList();
        
        // Split into batches of 25
        for (int i = 0; i < writeRequests.size(); i += 25) {
            int end = Math.min(i + 25, writeRequests.size());
            List<WriteRequest> batch = writeRequests.subList(i, end);
            
            BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName, batch))
                    .build();
            
            client.batchWriteItem(batchRequest);
        }
    }

    @Override
    public boolean isVersionReferenced(SchemaVersionRef schemaVersionRef) {
        // Query GSI1 to find all document sets, then check if any document references this schema version
        // This is a simplified implementation - in production, you might want a dedicated GSI for this
        
        String gsi1Pk = gsi1PkForTenantDocumentSets(tenantId);
        
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .indexName(GSI1_NAME)
                .keyConditionExpression("#gsi1pk = :gsi1pk")
                .expressionAttributeNames(Map.of("#gsi1pk", GSI1_PK))
                .expressionAttributeValues(Map.of(":gsi1pk", AttributeValue.builder().s(gsi1Pk).build()))
                .build();
        
        QueryResponse response = client.query(request);
        
        // For each document set, check if any document references this schema version
        for (Map<String, AttributeValue> item : response.items()) {
            if (item.containsKey("documentSetId")) {
                String docSetId = item.get("documentSetId").s();
                if (hasDocumentWithSchemaRef(docSetId, schemaVersionRef)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private boolean hasDocumentWithSchemaRef(String documentSetId, SchemaVersionRef schemaVersionRef) {
        String pk = documentSetPk(tenantId, documentSetId);
        
        // Query documents in this document set
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk AND begins_with(#sk, :docPrefix)")
                .filterExpression("#schemaId = :schemaId AND #schemaVersion = :schemaVersion")
                .expressionAttributeNames(Map.of(
                        "#pk", PK,
                        "#sk", SK,
                        "#schemaId", "schemaId",
                        "#schemaVersion", "schemaVersion"))
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s(pk).build(),
                        ":docPrefix", AttributeValue.builder().s(DOC_PREFIX).build(),
                        ":schemaId", AttributeValue.builder().s(schemaVersionRef.schemaId().toString()).build(),
                        ":schemaVersion", AttributeValue.builder().s(schemaVersionRef.version().value()).build()))
                .build();
        
        QueryResponse response = client.query(request);
        
        return !response.items().isEmpty();
    }

    private Schema reconstructFromItems(List<Map<String, AttributeValue>> items) {
        SchemaMetadata metadata = null;
        List<SchemaVersion> versions = new ArrayList<>();
        
        for (Map<String, AttributeValue> item : items) {
            String entityType = item.get("entityType").s();
            
            switch (entityType) {
                case "SCHEMA" -> metadata = mapper.fromSchemaMetadataItem(item);
                case "SCHEMA_VERSION" -> versions.add(mapper.fromSchemaVersionItem(item));
            }
        }
        
        if (metadata == null) {
            throw new IllegalStateException("Schema metadata not found in items");
        }
        
        return mapper.reconstructSchema(metadata, versions);
    }
}
