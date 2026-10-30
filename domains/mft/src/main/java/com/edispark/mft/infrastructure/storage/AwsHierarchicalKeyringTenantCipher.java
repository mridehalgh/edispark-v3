package com.edispark.mft.infrastructure.storage;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CommitmentPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.cryptography.keystore.KeyStore;
import software.amazon.cryptography.keystore.model.KMSConfiguration;
import software.amazon.cryptography.keystore.model.KeyStoreConfig;
import software.amazon.cryptography.materialproviders.IKeyring;
import software.amazon.cryptography.materialproviders.MaterialProviders;
import software.amazon.cryptography.materialproviders.model.CreateAwsKmsHierarchicalKeyringInput;
import software.amazon.cryptography.materialproviders.model.MaterialProvidersConfig;

public final class AwsHierarchicalKeyringTenantCipher implements TenantCipher {
    private final AwsCrypto crypto = AwsCrypto.builder()
            .withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt)
            .build();
    private final MaterialProviders providers = MaterialProviders.builder()
            .MaterialProvidersConfig(MaterialProvidersConfig.builder().build())
            .build();
    private final KeyStore keyStore;
    private final TenantBranchKeyResolver branchKeys;
    private final long cacheTtlSeconds;
    private final ConcurrentHashMap<String, IKeyring> keyrings = new ConcurrentHashMap<>();

    public AwsHierarchicalKeyringTenantCipher(DynamoDbClient dynamoDb, KmsClient kms, String tableName,
                                               String logicalName, String rootKeyArn,
                                               TenantBranchKeyResolver branchKeys, long cacheTtlSeconds) {
        if (cacheTtlSeconds < 1) throw new IllegalArgumentException("cache TTL must be positive");
        this.branchKeys = branchKeys;
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.keyStore = KeyStore.builder().KeyStoreConfig(KeyStoreConfig.builder()
                .ddbClient(dynamoDb)
                .ddbTableName(tableName)
                .logicalKeyStoreName(logicalName)
                .kmsClient(kms)
                .kmsConfiguration(KMSConfiguration.builder().kmsKeyArn(rootKeyArn).build())
                .build()).build();
    }

    @Override
    public byte[] encrypt(String tenantId, byte[] plaintext, Map<String, String> context) {
        Map<String, String> authenticatedContext = authenticatedContext(tenantId, context);
        return crypto.encryptData(keyring(tenantId), plaintext, authenticatedContext).getResult();
    }

    @Override
    public byte[] decrypt(String tenantId, byte[] ciphertext, Map<String, String> context) {
        Map<String, String> authenticatedContext = authenticatedContext(tenantId, context);
        return crypto.decryptData(keyring(tenantId), ciphertext, authenticatedContext).getResult();
    }

    private IKeyring keyring(String tenantId) {
        String branchKeyId = branchKeys.resolve(requireTenant(tenantId));
        return keyrings.computeIfAbsent(branchKeyId, ignored -> providers.CreateAwsKmsHierarchicalKeyring(
                CreateAwsKmsHierarchicalKeyringInput.builder()
                        .keyStore(keyStore)
                        .branchKeyId(branchKeyId)
                        .ttlSeconds(cacheTtlSeconds)
                        .build()));
    }

    private Map<String, String> authenticatedContext(String tenantId, Map<String, String> context) {
        Map<String, String> result = new HashMap<>(context);
        String existing = result.put("tenant", requireTenant(tenantId));
        if (existing != null && !existing.equals(tenantId)) {
            throw new IllegalArgumentException("encryption context tenant does not match the caller");
        }
        result.put("service", "edispark-mft");
        return Map.copyOf(result);
    }

    private String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        return tenantId;
    }
}
