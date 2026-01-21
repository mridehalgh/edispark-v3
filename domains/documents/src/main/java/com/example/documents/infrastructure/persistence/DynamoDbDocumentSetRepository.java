package com.example.documents.infrastructure.persistence;

import com.example.documents.domain.model.ContentHash;
import com.example.documents.domain.model.Derivative;
import com.example.documents.domain.model.DocumentId;
import com.example.documents.domain.model.DocumentSet;
import com.example.documents.domain.model.DocumentSetId;
import com.example.documents.domain.model.DocumentVersion;
import com.example.documents.domain.repository.DocumentSetRepository;
import com.example.documents.infrastructure.persistence.DocumentSetItemMapper.DocumentData;
import com.example.documents.infrastructure.persistence.DocumentSetItemMapper.DocumentSetMetadata;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.documents.infrastructure.persistence.DynamoDbTableConfig.*;

/**
 * DynamoDB implementation of DocumentSetRepository.
 * 
 * <p>This repository uses a single-table design where all DocumentSet entities
 * (DocumentSet metadata, Documents, DocumentVersions, Derivatives) are stored
 * in the same table with different sort key patterns.</p>
 * 
 * <p>Requirements: 10.1, 10.2, 10.5</p>
 */
public class DynamoDbDocumentSetRepository implements DocumentSetRepository {

    private final DynamoDbClient client;
    private final String tableName;
    private final String tenantId;
    private final DocumentSetItemMapper mapper;

    public DynamoDbDocumentSetRepository(DynamoDbClient client, String tableName) {
        this(client, tableName, DEFAULT_TENANT);
    }

    public DynamoDbDocumentSetRepository(DynamoDbClient client, String tableName, String tenantId) {
        this.client = client;
        this.tableName = tableName;
        this.tenantId = tenantId;
        this.mapper = new DocumentSetItemMapper(tenantId);
    }

    @Override
    public Optional<DocumentSet> findById(DocumentSetId id) {
        String pk = documentSetPk(tenantId, id.toString());
        
        // Query all items for this DocumentSet
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
    public List<DocumentSet> findAll() {
        // Query GSI1 to get all document sets for this tenant
        String gsi1Pk = gsi1PkForTenantDocumentSets(tenantId);
        
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .indexName(GSI1_NAME)
                .keyConditionExpression("#gsi1pk = :gsi1pk")
                .expressionAttributeNames(Map.of("#gsi1pk", GSI1_PK))
                .expressionAttributeValues(Map.of(":gsi1pk", AttributeValue.builder().s(gsi1Pk).build()))
                .build();
        
        QueryResponse response = client.query(request);
        
        // Extract document set IDs from the GSI results
        Set<DocumentSetId> documentSetIds = new HashSet<>();
        for (Map<String, AttributeValue> item : response.items()) {
            if (item.containsKey("documentSetId")) {
                documentSetIds.add(DocumentSetId.fromString(item.get("documentSetId").s()));
            }
        }
        
        // Fetch full document sets
        List<DocumentSet> result = new ArrayList<>();
        for (DocumentSetId docSetId : documentSetIds) {
            findById(docSetId).ifPresent(result::add);
        }
        
        return result;
    }

    @Override
    public void save(DocumentSet documentSet) {
        List<Map<String, AttributeValue>> items = mapper.toItems(documentSet);
        
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
    public void delete(DocumentSetId id) {
        String pk = documentSetPk(tenantId, id.toString());
        
        // First, query all items for this DocumentSet
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", PK))
                .expressionAttributeValues(Map.of(":pk", AttributeValue.builder().s(pk).build()))
                .projectionExpression("#pk, #sk")
                .expressionAttributeNames(Map.of("#pk", PK, "#sk", SK))
                .build();
        
        QueryResponse response = client.query(queryRequest);
        
        if (response.items().isEmpty()) {
            return;
        }
        
        // Delete all items
        List<WriteRequest> deleteRequests = response.items().stream()
                .map(item -> WriteRequest.builder()
                        .deleteRequest(DeleteRequest.builder()
                                .key(Map.of(
                                        PK, item.get(PK),
                                        SK, item.get(SK)))
                                .build())
                        .build())
                .toList();
        
        // Split into batches of 25
        for (int i = 0; i < deleteRequests.size(); i += 25) {
            int end = Math.min(i + 25, deleteRequests.size());
            List<WriteRequest> batch = deleteRequests.subList(i, end);
            
            BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName, batch))
                    .build();
            
            client.batchWriteItem(batchRequest);
        }
    }

    @Override
    public List<DocumentSet> findByContentHash(ContentHash contentHash) {
        // Query GSI2 to find document versions with this content hash
        String gsi2Pk = gsi2PkForContentHash(tenantId, contentHash.toFullString());
        
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .indexName(GSI2_NAME)
                .keyConditionExpression("#gsi2pk = :gsi2pk")
                .expressionAttributeNames(Map.of("#gsi2pk", GSI2_PK))
                .expressionAttributeValues(Map.of(":gsi2pk", AttributeValue.builder().s(gsi2Pk).build()))
                .build();
        
        QueryResponse response = client.query(request);
        
        // Extract unique document set IDs
        Set<DocumentSetId> documentSetIds = new HashSet<>();
        for (Map<String, AttributeValue> item : response.items()) {
            if (item.containsKey("documentSetId")) {
                documentSetIds.add(DocumentSetId.fromString(item.get("documentSetId").s()));
            }
        }
        
        // Fetch full document sets
        List<DocumentSet> result = new ArrayList<>();
        for (DocumentSetId docSetId : documentSetIds) {
            findById(docSetId).ifPresent(result::add);
        }
        
        return result;
    }

    private DocumentSet reconstructFromItems(List<Map<String, AttributeValue>> items) {
        DocumentSetMetadata metadata = null;
        List<DocumentData> documentDataList = new ArrayList<>();
        Map<DocumentId, List<DocumentVersion>> versionsByDocument = new HashMap<>();
        Map<DocumentId, List<Derivative>> derivativesByDocument = new HashMap<>();
        
        for (Map<String, AttributeValue> item : items) {
            String entityType = item.get("entityType").s();
            
            switch (entityType) {
                case "DOCUMENT_SET" -> metadata = mapper.fromDocumentSetMetadataItem(item);
                case "DOCUMENT" -> documentDataList.add(mapper.fromDocumentItem(item));
                case "DOCUMENT_VERSION" -> {
                    DocumentId docId = DocumentId.fromString(item.get("documentId").s());
                    DocumentVersion version = mapper.fromDocumentVersionItem(item);
                    versionsByDocument.computeIfAbsent(docId, k -> new ArrayList<>()).add(version);
                }
                case "DERIVATIVE" -> {
                    DocumentId docId = DocumentId.fromString(item.get("documentId").s());
                    Derivative derivative = mapper.fromDerivativeItem(item);
                    derivativesByDocument.computeIfAbsent(docId, k -> new ArrayList<>()).add(derivative);
                }
            }
        }
        
        if (metadata == null) {
            throw new IllegalStateException("DocumentSet metadata not found in items");
        }
        
        return mapper.reconstructDocumentSet(metadata, documentDataList, versionsByDocument, derivativesByDocument);
    }
}
