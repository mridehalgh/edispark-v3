package com.edispark.integrationapplication.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Component
@Profile("!local & !openapi-export")
final class IntegrationDynamoStore {
    private final DynamoDbClient dynamo;
    private final String table;

    IntegrationDynamoStore(DynamoDbClient dynamo, @Value("${integration.aws.metadata-table}") String table) {
        if (table == null || table.isBlank()) throw new IllegalStateException("INTEGRATION_METADATA_TABLE is required");
        this.dynamo = dynamo;
        this.table = table;
    }

    void put(String tenantId, String sortKey, String type, String json) {
        dynamo.putItem(PutItemRequest.builder().tableName(table).item(item(tenantId, sortKey, type, json)).build());
    }

    boolean putPairIfAbsent(String tenantId, String firstKey, String firstType, String firstJson,
            String uniqueKey, String uniqueJson) {
        var first = Put.builder().tableName(table).item(item(tenantId, firstKey, firstType, firstJson))
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build();
        var unique = Put.builder().tableName(table).item(item(tenantId, uniqueKey, "IDEMPOTENCY", uniqueJson))
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)").build();
        try {
            dynamo.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    TransactWriteItem.builder().put(first).build(),
                    TransactWriteItem.builder().put(unique).build()).build());
            return true;
        } catch (TransactionCanceledException exception) {
            return false;
        }
    }

    Optional<String> get(String tenantId, String sortKey) {
        var response = dynamo.getItem(GetItemRequest.builder().tableName(table).consistentRead(true)
                .key(Map.of("pk", text(pk(tenantId)), "sk", text(sortKey))).build());
        return Optional.ofNullable(response.item().get("data")).map(AttributeValue::s);
    }

    List<String> list(String tenantId, String prefix) {
        List<String> results = new ArrayList<>();
        Map<String, AttributeValue> cursor = Map.of();
        do {
            var response = dynamo.query(QueryRequest.builder().tableName(table).consistentRead(true)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(":pk", text(pk(tenantId)), ":prefix", text(prefix)))
                    .exclusiveStartKey(cursor).build());
            response.items().stream().map(item -> item.get("data").s()).forEach(results::add);
            cursor = response.lastEvaluatedKey();
        } while (cursor != null && !cursor.isEmpty());
        return List.copyOf(results);
    }

    private Map<String, AttributeValue> item(String tenantId, String sortKey, String type, String json) {
        return Map.of("pk", text(pk(tenantId)), "sk", text(sortKey), "entityType", text(type), "data", text(json));
    }
    private String pk(String tenantId) { return "TENANT#" + tenantId; }
    private static AttributeValue text(String value) { return AttributeValue.builder().s(value).build(); }
}
