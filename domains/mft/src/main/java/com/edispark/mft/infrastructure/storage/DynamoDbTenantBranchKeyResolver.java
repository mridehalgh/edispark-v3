package com.edispark.mft.infrastructure.storage;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** MFT adapter to the cell-wide tenant branch registry. */
public final class DynamoDbTenantBranchKeyResolver implements TenantBranchKeyResolver {
    private final com.edispark.crypto.DynamoDbTenantBranchKeyResolver delegate;

    public DynamoDbTenantBranchKeyResolver(DynamoDbClient client, String tableName) {
        delegate = new com.edispark.crypto.DynamoDbTenantBranchKeyResolver(client, tableName);
    }

    @Override public String resolve(String tenantId) { return delegate.resolve(tenantId); }
}
