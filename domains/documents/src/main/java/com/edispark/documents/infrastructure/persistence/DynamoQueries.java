package com.edispark.documents.infrastructure.persistence;

import java.util.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

final class DynamoQueries {
    private DynamoQueries() { }

    static List<Map<String, AttributeValue>> all(DynamoDbClient client, QueryRequest request) {
        List<Map<String, AttributeValue>> result = new ArrayList<>();
        var next = request;
        while (true) {
            var page = client.query(next);
            result.addAll(page.items());
            if (page.lastEvaluatedKey().isEmpty()) return List.copyOf(result);
            next = request.toBuilder().exclusiveStartKey(page.lastEvaluatedKey()).build();
        }
    }
}
