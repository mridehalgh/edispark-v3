package com.edispark.mft.infrastructure.storage;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

public final class DynamoDbTenantBranchKeyResolver implements TenantBranchKeyResolver {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoDbTenantBranchKeyResolver(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public String resolve(String tenantId) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .consistentRead(true)
                .key(Map.of("pk", string("TENANT#" + tenantId), "sk", string("KEYRING")))
                .projectionExpression("branchKeyId")
                .build()).item();
        AttributeValue branchKeyId = item.get("branchKeyId");
        if (branchKeyId == null || branchKeyId.s() == null || branchKeyId.s().isBlank()) {
            throw new IllegalStateException("Tenant encryption branch key is not provisioned");
        }
        return branchKeyId.s();
    }

    private AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
