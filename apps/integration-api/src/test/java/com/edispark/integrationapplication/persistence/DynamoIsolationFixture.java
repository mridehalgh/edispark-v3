package com.edispark.integrationapplication.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Proxy;
import java.util.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** Deterministic SDK-boundary fake with tenant partitions, pagination, CAS, and atomic conditional transactions. */
final class DynamoIsolationFixture {
    private final Map<String, Map<String, AttributeValue>> records = new HashMap<>();

    ObjectMapper json() { return new ObjectMapper().registerModule(new JavaTimeModule()); }

    IntegrationDynamoStore store() {
        var client = (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(),
                new Class<?>[]{DynamoDbClient.class}, (proxy, method, args) -> invoke(method.getName(), args));
        return new IntegrationDynamoStore(client, "isolation-fixture");
    }

    private synchronized Object invoke(String method, Object[] args) {
        return switch (method) {
            case "getItem" -> GetItemResponse.builder().item(records.getOrDefault(key(((GetItemRequest) args[0]).key()), Map.of())).build();
            case "putItem" -> put((PutItemRequest) args[0]);
            case "deleteItem" -> {
                records.remove(key(((DeleteItemRequest) args[0]).key()));
                yield DeleteItemResponse.builder().build();
            }
            case "query" -> query((QueryRequest) args[0]);
            case "transactWriteItems" -> transact((TransactWriteItemsRequest) args[0]);
            default -> throw new AssertionError("Unexpected SDK access: " + method);
        };
    }

    private PutItemResponse put(PutItemRequest request) {
        if (!allowed(request.item(), request.conditionExpression(), request.expressionAttributeValues())) {
            throw ConditionalCheckFailedException.builder().message("fixture condition mismatch").build();
        }
        records.put(key(request.item()), request.item());
        return PutItemResponse.builder().build();
    }

    private TransactWriteItemsResponse transact(TransactWriteItemsRequest request) {
        for (var operation : request.transactItems()) {
            var put = operation.put();
            if (!allowed(put.item(), put.conditionExpression(), put.expressionAttributeValues())) {
                throw TransactionCanceledException.builder().message("fixture transaction conflict").build();
            }
        }
        request.transactItems().forEach(operation -> records.put(key(operation.put().item()), operation.put().item()));
        return TransactWriteItemsResponse.builder().build();
    }

    private boolean allowed(Map<String, AttributeValue> item, String condition, Map<String, AttributeValue> values) {
        if (condition == null) return true;
        var current = records.get(key(item));
        if (condition.startsWith("attribute_not_exists")) return current == null;
        return current != null && Objects.equals(current.get("version"), values.get(":expected"));
    }

    private QueryResponse query(QueryRequest request) {
        if (!"pk = :pk AND begins_with(sk, :prefix)".equals(request.keyConditionExpression())) {
            throw new AssertionError("Unscoped query");
        }
        String tenant = request.expressionAttributeValues().get(":pk").s();
        String prefix = request.expressionAttributeValues().get(":prefix").s();
        String cursor = request.exclusiveStartKey().isEmpty() ? "" : request.exclusiveStartKey().get("sk").s();
        var matches = records.values().stream().filter(item -> item.get("pk").s().equals(tenant))
                .filter(item -> item.get("sk").s().startsWith(prefix)).filter(item -> item.get("sk").s().compareTo(cursor) > 0)
                .sorted(Comparator.comparing(item -> item.get("sk").s())).toList();
        var page = matches.stream().limit(2).toList();
        var response = QueryResponse.builder().items(page);
        if (matches.size() > page.size()) {
            var last = page.getLast();
            response.lastEvaluatedKey(Map.of("pk", last.get("pk"), "sk", last.get("sk")));
        }
        return response.build();
    }

    private String key(Map<String, AttributeValue> item) { return item.get("pk").s() + "\u0000" + item.get("sk").s(); }
}
