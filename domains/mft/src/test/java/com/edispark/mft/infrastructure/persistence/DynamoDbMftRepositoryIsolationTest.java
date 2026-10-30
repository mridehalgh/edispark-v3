package com.edispark.mft.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.edispark.mft.testutil.AwsClientFake;
import com.edispark.mft.domain.model.MdnMode;
import com.edispark.mft.domain.model.PartnerProfile;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

class DynamoDbMftRepositoryIsolationTest {
    @Test
    void partnerLookupAlwaysUsesTenantPartitionAndConsistentRead() {
        AtomicReference<GetItemRequest> captured = new AtomicReference<>();
        DynamoDbClient dynamoDb = AwsClientFake.create(DynamoDbClient.class, (proxy, method, arguments) -> {
            captured.set((GetItemRequest) arguments[0]);
            return GetItemResponse.builder().build();
        });
        UUID partnerId = UUID.randomUUID();

        assertThat(new DynamoDbMftRepository(dynamoDb, "mft").findPartner("tenant-a", partnerId)).isEmpty();
        assertThat(captured.get().consistentRead()).isTrue();
        assertThat(captured.get().key().get("pk").s()).isEqualTo("TENANT#tenant-a");
        assertThat(captured.get().key().get("sk").s()).isEqualTo("PARTNER#" + partnerId);
    }

    @Test
    void partnerListsUseAKeyConditionAndNeverAFilterBasedTenantBoundary() {
        AtomicReference<QueryRequest> captured = new AtomicReference<>();
        DynamoDbClient dynamoDb = AwsClientFake.create(DynamoDbClient.class, (proxy, method, arguments) -> {
            captured.set((QueryRequest) arguments[0]);
            return QueryResponse.builder().items(List.of()).build();
        });

        new DynamoDbMftRepository(dynamoDb, "mft").listPartners("tenant-a");

        assertThat(captured.get().keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :prefix)");
        assertThat(captured.get().filterExpression()).isNull();
        assertThat(captured.get().expressionAttributeValues().get(":pk").s()).isEqualTo("TENANT#tenant-a");
        assertThat(captured.get().expressionAttributeValues().get(":prefix").s()).isEqualTo("PARTNER#");
    }

    @Test
    void savingPartnerAtomicallyClaimsGlobalAs2PairBeforeTenantRecord() {
        AtomicReference<TransactWriteItemsRequest> captured = new AtomicReference<>();
        DynamoDbClient dynamoDb = AwsClientFake.create(DynamoDbClient.class, (proxy, method, arguments) -> {
            captured.set((TransactWriteItemsRequest) arguments[0]);
            return TransactWriteItemsResponse.builder().build();
        });
        PartnerProfile partner = new PartnerProfile(UUID.randomUUID(), "tenant-a", "Retailer", "EDISPARK-A",
                "RETAILER-A", URI.create("https://retailer.example/as2"), UUID.randomUUID(), UUID.randomUUID(),
                MdnMode.SYNCHRONOUS, true, true, true, false, Instant.parse("2026-10-30T12:00:00Z"));

        new DynamoDbMftRepository(dynamoDb, "mft").savePartner(partner);

        assertThat(captured.get().transactItems()).hasSize(2);
        Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> owner =
                captured.get().transactItems().getFirst().put().item();
        assertThat(owner.get("pk").s()).startsWith("AS2PAIR#");
        assertThat(owner.get("sk").s()).isEqualTo("OWNER");
        assertThat(owner.get("tenantId").s()).isEqualTo("tenant-a");
        assertThat(captured.get().transactItems().getFirst().put().conditionExpression())
                .isEqualTo("attribute_not_exists(pk)");
        assertThat(captured.get().transactItems().get(1).put().item().get("pk").s())
                .isEqualTo("TENANT#tenant-a");
    }
}
