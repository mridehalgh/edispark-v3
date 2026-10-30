package com.edispark.mft.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.edispark.mft.testutil.AwsClientFake;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

class DynamoDbTenantBranchKeyResolverTest {
    @Test
    void resolvesOnlyTheRequestedTenantsBranchKeyWithAConsistentRead() {
        AtomicReference<GetItemRequest> captured = new AtomicReference<>();
        DynamoDbClient dynamoDb = AwsClientFake.create(DynamoDbClient.class, (proxy, method, arguments) -> {
            captured.set((GetItemRequest) arguments[0]);
            return GetItemResponse.builder().item(Map.of("branchKeyId", string("branch-a"))).build();
        });

        String result = new DynamoDbTenantBranchKeyResolver(dynamoDb, "mft-metadata").resolve("tenant-a");

        assertThat(result).isEqualTo("branch-a");
        assertThat(captured.get().consistentRead()).isTrue();
        assertThat(captured.get().key().get("pk").s()).isEqualTo("TENANT#tenant-a");
        assertThat(captured.get().key().get("sk").s()).isEqualTo("KEYRING");
    }

    @Test
    void missingBranchKeyFailsClosedInsteadOfUsingAnotherTenantOrRootKey() {
        DynamoDbClient dynamoDb = AwsClientFake.create(DynamoDbClient.class,
                (proxy, method, arguments) -> GetItemResponse.builder().build());

        assertThatThrownBy(() -> new DynamoDbTenantBranchKeyResolver(dynamoDb, "mft-metadata")
                .resolve("tenant-without-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tenant encryption branch key is not provisioned");
    }

    private static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
