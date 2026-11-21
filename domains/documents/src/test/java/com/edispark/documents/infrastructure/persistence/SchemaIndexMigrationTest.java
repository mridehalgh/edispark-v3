package com.edispark.documents.infrastructure.persistence;

import com.edispark.documents.domain.model.*;
import com.edispark.identifiers.tenant.TenantId;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import static com.edispark.documents.infrastructure.persistence.DynamoDbTableConfig.*;
import static org.assertj.core.api.Assertions.*;

class SchemaIndexMigrationTest {
    @Test void dryRunApplyAndReplayAreIdempotentAndDoNotRewriteOwnership() {
        String owner = TenantId.newTenantId().asString();
        var schema = Schema.create("Retained schema", SchemaFormat.JSON_SCHEMA);
        var item = new HashMap<>(new SchemaItemMapper(owner).toSchemaMetadataItem(schema));
        item.remove(GSI3_PK);
        item.remove(GSI3_SK);
        List<UpdateItemRequest> writes = new ArrayList<>();
        var client = (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(), new Class<?>[]{DynamoDbClient.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "scan" -> ScanResponse.builder().items(item).build();
                    case "updateItem" -> {
                        var request = (UpdateItemRequest) args[0];
                        writes.add(request);
                        item.put(GSI3_PK, request.expressionAttributeValues().get(":gpk"));
                        item.put(GSI3_SK, request.expressionAttributeValues().get(":gsk"));
                        yield UpdateItemResponse.builder().build();
                    }
                    default -> throw new AssertionError(method.getName());
                });
        var migration = new SchemaIndexMigration(client, "test");
        assertThat(migration.run(false).changes()).isEqualTo(1);
        assertThat(writes).isEmpty();
        assertThat(migration.run(true).changes()).isEqualTo(1);
        assertThat(writes).singleElement().satisfies(request -> {
            assertThat(request.conditionExpression()).contains("#owner = :owner");
            assertThat(request.expressionAttributeValues().get(":owner").s()).isEqualTo(owner);
        });
        assertThat(migration.run(true).changes()).isZero();
        assertThat(item.get("tenantId").s()).isEqualTo(owner);
    }
}
