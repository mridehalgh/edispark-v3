package com.edispark.mft.infrastructure.storage;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;

public final class AwsHierarchicalKeyringTenantCipher implements TenantCipher {
    private final com.edispark.crypto.HierarchicalTenantCipher delegate;

    public AwsHierarchicalKeyringTenantCipher(DynamoDbClient dynamoDb, KmsClient kms, String tableName,
            String logicalName, String rootKeyArn, TenantBranchKeyResolver branches, long cacheTtlSeconds) {
        delegate = new com.edispark.crypto.HierarchicalTenantCipher(dynamoDb, kms, tableName,
                logicalName, rootKeyArn, branches, cacheTtlSeconds, "edispark-mft");
    }

    @Override public byte[] encrypt(String tenantId, byte[] plaintext, Map<String, String> context) {
        return delegate.encrypt(tenantId, plaintext, context);
    }
    @Override public byte[] decrypt(String tenantId, byte[] ciphertext, Map<String, String> context) {
        return delegate.decrypt(tenantId, ciphertext, context);
    }
}
