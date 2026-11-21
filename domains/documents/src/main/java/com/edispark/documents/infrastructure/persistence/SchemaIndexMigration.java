package com.edispark.documents.infrastructure.persistence;

import java.util.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import static com.edispark.documents.infrastructure.persistence.DynamoDbTableConfig.*;

/** Administrative backfill only. SCAN is never used by the tenant-facing repository. */
public record SchemaIndexMigration(DynamoDbClient client, String tableName) {
    public record Result(int inspected, int changes, boolean applied) { }

    public Result run(boolean apply) {
        int inspected = 0;
        int changes = 0;
        Map<String, AttributeValue> cursor = Map.of();
        do {
            var request = ScanRequest.builder().tableName(tableName)
                    .filterExpression("#type = :schema")
                    .expressionAttributeNames(Map.of("#type", "entityType"))
                    .expressionAttributeValues(Map.of(":schema", text("SCHEMA")));
            if (!cursor.isEmpty()) request.exclusiveStartKey(cursor);
            var page = client.scan(request.build());
            for (var item : page.items()) {
                inspected++;
                if (backfill(item, apply)) changes++;
            }
            cursor = page.lastEvaluatedKey();
        } while (!cursor.isEmpty());
        return new Result(inspected, changes, apply);
    }

    private boolean backfill(Map<String, AttributeValue> item, boolean apply) {
        String tenant = item.get("tenantId").s();
        String schema = item.get("schemaId").s();
        String expectedPk = schemaPk(tenant, schema);
        if (!text(expectedPk).equals(item.get(PK)) || !text("METADATA").equals(item.get(SK))) {
            throw new SecurityException("Schema ownership metadata is inconsistent");
        }
        var tenantIndex = text(tenantSchemasPk(tenant));
        var schemaIndex = text("SCHEMA#" + schema);
        if (tenantIndex.equals(item.get(GSI3_PK)) && schemaIndex.equals(item.get(GSI3_SK))) return false;
        if (apply) client.updateItem(UpdateItemRequest.builder().tableName(tableName)
                .key(Map.of(PK, item.get(PK), SK, item.get(SK)))
                .conditionExpression("#owner = :owner AND #schema = :schema")
                .updateExpression("SET #gpk = :gpk, #gsk = :gsk")
                .expressionAttributeNames(Map.of("#owner", "tenantId", "#schema", "schemaId", "#gpk", GSI3_PK, "#gsk", GSI3_SK))
                .expressionAttributeValues(Map.of(":owner", text(tenant), ":schema", text(schema), ":gpk", tenantIndex, ":gsk", schemaIndex))
                .build());
        return true;
    }

    private AttributeValue text(String value) { return AttributeValue.builder().s(value).build(); }
}
