package com.edispark.integrationapplication.persistence;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class DynamoOutboundControlAllocatorTest {
    @Test void retainsPerConnectionControlsAcrossProvidersAndReplaysWithinTheTenant() {
        var rows = new HashMap<String, Map<String, AttributeValue>>();
        var client = (DynamoDbClient) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DynamoDbClient.class}, (proxy, method, args) -> {
            synchronized (rows) {
                return switch (method.getName()) {
                    case "getItem" -> GetItemResponse.builder().item(rows.getOrDefault(key(((GetItemRequest) args[0]).key()), Map.of())).build();
                    case "putItem" -> {
                        var request = (PutItemRequest) args[0]; var key = key(request.item());
                        if (request.conditionExpression() != null && rows.containsKey(key)) throw ConditionalCheckFailedException.builder().build();
                        rows.put(key, request.item()); yield PutItemResponse.builder().build();
                    }
                    case "transactWriteItems" -> {
                        var puts = ((TransactWriteItemsRequest) args[0]).transactItems().stream().map(TransactWriteItem::put).toList();
                        var first = puts.getFirst(); var current = rows.get(key(first.item()));
                        if (current == null || !current.get("version").equals(first.expressionAttributeValues().get(":expected"))
                                || rows.containsKey(key(puts.get(1).item()))) throw TransactionCanceledException.builder().build();
                        puts.forEach(put -> rows.put(key(put.item()), put.item())); yield TransactWriteItemsResponse.builder().build();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
        });
        var store = new IntegrationDynamoStore(client, "table");
        var first = new DynamoOutboundControlAllocator(store, new ObjectMapper());
        var restarted = new DynamoOutboundControlAllocator(store, new ObjectMapper());
        var connection = UUID.randomUUID(); var exchange = UUID.randomUUID();
        within("2OnHVzeAJKwm0ThFLcy1HfLHGPm", () -> {
            assertThat(first.reserve(connection, exchange).transmission()).isEqualTo(1);
            assertThat(restarted.reserve(connection, exchange).fileGeneration()).isEqualTo(1);
            assertThat(restarted.reserve(connection, UUID.randomUUID()).transmission()).isEqualTo(2);
            assertThatThrownBy(() -> first.reserve(UUID.randomUUID(), exchange)).isInstanceOf(IllegalStateException.class);
            assertThat(first.reserve(UUID.randomUUID(), UUID.randomUUID()).transmission()).isEqualTo(1);
        });
        within("2OnHVzeAJKwm0ThFLcy1HfLHGPn", () ->
                assertThat(restarted.reserve(connection, exchange).transmission()).isEqualTo(1));
        assertThat(rows).hasSize(7);
    }
    private static String key(Map<String, AttributeValue> item) { return item.get("pk").s() + "|" + item.get("sk").s(); }
    private static void within(String tenant, Runnable work) {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenant)), work);
    }
}
