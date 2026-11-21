package com.edispark.documents.infrastructure.persistence;

import com.edispark.documents.application.handler.InvalidPaginationTokenException;
import com.edispark.documents.domain.model.*;
import com.edispark.identifiers.tenant.TenantId;
import com.edispark.pagination.Page;
import io.cottn.core.tenancy.NoTenantSetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import static com.edispark.documents.infrastructure.persistence.DynamoDbTableConfig.*;
import static org.assertj.core.api.Assertions.*;

class TenantPersistenceBoundaryTest {
    @Test void foreignAndMalformedCursorsFailBeforeDynamoDbAccess() {
        var calls = new AtomicInteger();
        var client = fake(query -> { calls.incrementAndGet(); throw new AssertionError("Unexpected data access"); });
        String current = TenantId.newTenantId().asString();
        String foreign = TenantId.newTenantId().asString();
        var repository = new DynamoDbDocumentSetRepository(client, "test", current);
        var cursor = Map.of(PK, text(documentSetPk(foreign, UUID.randomUUID().toString())), SK, text("METADATA"),
                GSI1_PK, text(gsi1PkForTenantDocumentSets(foreign)), GSI1_SK, text("cursor"));
        assertThatThrownBy(() -> repository.findAll(Page.next(10, PaginationTokenCodec.encode(cursor))))
                .isInstanceOf(InvalidPaginationTokenException.class);
        assertThatThrownBy(() -> repository.findAll(Page.next(10, PaginationTokenCodec.encode(Map.of(PK, text("bad"))))))
                .isInstanceOf(InvalidPaginationTokenException.class);
        assertThatThrownBy(() -> new TenantScopedSchemaRepository(client, "test").findAll())
                .isInstanceOf(NoTenantSetException.class);
        assertThat(calls.get()).isZero();
    }

    @Property(tries = 100)
    void schemaListingFollowsAllTenantPages(@ForAll @IntRange(min = 1, max = 25) int count,
                                           @ForAll @IntRange(min = 1, max = 8) int pageSize) {
        String owner = TenantId.newTenantId().asString();
        var mapper = new SchemaItemMapper(owner);
        List<Map<String, AttributeValue>> owned = new ArrayList<>();
        Map<String, List<Map<String, AttributeValue>>> aggregates = new HashMap<>();
        for (int index = 0; index < count; index++) {
            var schema = Schema.create("Owned schema " + index, SchemaFormat.JSON_SCHEMA);
            owned.add(mapper.toSchemaMetadataItem(schema));
            aggregates.put(schemaPk(owner, schema.id().toString()), mapper.toItems(schema));
        }
        var client = fake(query -> {
            if (GSI3_NAME.equals(query.indexName())) {
                assertThat(query.expressionAttributeValues().get(":tenant").s()).isEqualTo(tenantSchemasPk(owner));
                assertThat(query.filterExpression()).isNull();
                int start = offset(owned, query.exclusiveStartKey());
                int end = Math.min(start + pageSize, owned.size());
                var response = QueryResponse.builder().items(owned.subList(start, end));
                if (end < owned.size()) response.lastEvaluatedKey(Map.of(PK, owned.get(end - 1).get(PK)));
                return response.build();
            }
            String key = query.expressionAttributeValues().get(":pk").s();
            assertThat(key).startsWith("TENANT#" + owner + "#SCHEMA#");
            return QueryResponse.builder().items(aggregates.get(key)).build();
        });
        var schemas = new DynamoDbSchemaRepository(client, "test", owner).findAll();
        assertThat(schemas).hasSize(count);
        assertThat(schemas.stream().map(Schema::id).distinct().count()).isEqualTo(count);
    }

    private int offset(List<Map<String, AttributeValue>> items, Map<String, AttributeValue> cursor) {
        if (cursor.isEmpty()) return 0;
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).get(PK).equals(cursor.get(PK))) return index + 1;
        }
        throw new AssertionError("Unknown continuation key");
    }

    private DynamoDbClient fake(java.util.function.Function<QueryRequest, QueryResponse> query) {
        return (DynamoDbClient) Proxy.newProxyInstance(DynamoDbClient.class.getClassLoader(), new Class<?>[]{DynamoDbClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("query")) return query.apply((QueryRequest) args[0]);
                    throw new AssertionError("Unsupported or unscoped data access: " + method.getName());
                });
    }

    private AttributeValue text(String value) { return AttributeValue.builder().s(value).build(); }
}
