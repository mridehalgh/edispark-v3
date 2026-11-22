package com.edispark.integrationapplication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.transformations.domain.ReferenceTableRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cottn.core.tenancy.TenantExecution;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

class ReferenceTablesTest {
    private static final String FIRST = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
    private static final String SECOND = "2OnHVzeAJKwm0ThFLcy1HfLHGPn";

    @Test void identicalRevisionIdsAreIsolatedAndPublishingCannotChangeAnExistingRow() {
        var tables = new ReferenceTables(new IntegrationDynamoStore(client(), "table"), new ObjectMapper());
        var id = UUID.randomUUID();
        var original = revision(id, null, "SITE-A");
        var replacement = revision(UUID.randomUUID(), id, "SITE-B");
        var foreign = revision(id, null, "SITE-C");

        TenantExecution.run(FIRST, () -> {
            assertThat(tables.publish(original)).isEqualTo(original);
            assertThat(tables.publish(original)).isEqualTo(original);
            assertThat(tables.publish(replacement)).isEqualTo(replacement);
            assertThatThrownBy(() -> tables.publish(foreign)).hasMessageContaining("already exists");
            assertThat(tables.require(id).requireRow("0007")).containsEntry("erpSite", "SITE-A");
        });
        TenantExecution.run(SECOND, () -> {
            assertThatThrownBy(() -> tables.require(replacement.id())).hasMessageContaining("unavailable");
            assertThatThrownBy(() -> tables.publish(replacement)).hasMessageContaining("unavailable");
            assertThat(tables.publish(foreign)).isEqualTo(foreign);
            assertThat(tables.require(id).requireRow("0007")).containsEntry("erpSite", "SITE-C");
        });
        TenantExecution.run(FIRST, () -> assertThat(tables.require(id)).isEqualTo(original));
    }

    private ReferenceTableRevision revision(UUID id, UUID previous, String site) {
        return new ReferenceTableRevision(id, previous, "Sites", "code",
                List.of(Map.of("code", "0007", "erpSite", site)));
    }

    private DynamoDbClient client() {
        var items = new HashMap<List<AttributeValue>, Map<String, AttributeValue>>();
        return (DynamoDbClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{DynamoDbClient.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getItem" -> {
                        var key = key(((GetItemRequest) arguments[0]).key());
                        yield GetItemResponse.builder().item(items.getOrDefault(key, Map.of())).build();
                    }
                    case "putItem" -> {
                        var request = (PutItemRequest) arguments[0];
                        if (request.conditionExpression() != null
                                && items.putIfAbsent(key(request.item()), request.item()) != null) {
                            throw ConditionalCheckFailedException.builder().build();
                        }
                        yield PutItemResponse.builder().build();
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private List<AttributeValue> key(Map<String, AttributeValue> attributes) {
        return List.of(attributes.get("pk"), attributes.get("sk"));
    }
}
