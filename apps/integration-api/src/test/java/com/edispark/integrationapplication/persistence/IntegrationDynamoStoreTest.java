package com.edispark.integrationapplication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class IntegrationDynamoStoreTest {
    @Test void omitsTheInitialCursorAndFollowsEveryRetainedTenantPage() {
        List<QueryRequest> requests = new ArrayList<>();
        var key = Map.of("pk", text("TENANT#tenant"), "sk", text("EXCHANGE#one"));
        var client = (DynamoDbClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DynamoDbClient.class}, (proxy, method, arguments) -> {
            if (!method.getName().equals("query")) throw new UnsupportedOperationException(method.getName());
            requests.add((QueryRequest) arguments[0]);
            return requests.size() == 1 ? QueryResponse.builder().items(Map.of("data", text("first"))).lastEvaluatedKey(key).build()
                    : QueryResponse.builder().items(Map.of("data", text("second"))).build();
        });
        assertThat(new IntegrationDynamoStore(client, "local-table").list("tenant", "EXCHANGE#")).containsExactly("first", "second");
        assertThat(requests.getFirst().hasExclusiveStartKey()).isFalse();
        assertThat(requests.get(1).exclusiveStartKey()).isEqualTo(key);
        assertThat(requests).allSatisfy(request -> assertThat(request.expressionAttributeValues().get(":pk").s()).isEqualTo("TENANT#tenant"));
    }
    private static AttributeValue text(String value) { return AttributeValue.builder().s(value).build(); }
}
