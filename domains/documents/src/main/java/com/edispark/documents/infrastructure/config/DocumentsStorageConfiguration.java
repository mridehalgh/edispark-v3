package com.edispark.documents.infrastructure.config;

import com.edispark.crypto.*;
import com.edispark.documents.domain.repository.ContentStore;
import com.edispark.documents.infrastructure.storage.S3ContentStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@Profile("production & !openapi-export")
public class DocumentsStorageConfiguration {
    @Bean(destroyMethod = "close")
    KmsClient documentsKms(@Value("${aws.region}") String region) {
        return KmsClient.builder().region(Region.of(region)).build();
    }

    @Bean
    TenantCipher documentsTenantCipher(@Qualifier("dynamoDbClient") DynamoDbClient dynamo,
            @Qualifier("documentsKms") KmsClient kms,
            @Value("${documents.encryption.registry-table:}") String registry,
            @Value("${documents.encryption.key-store-table:}") String keyStore,
            @Value("${documents.encryption.logical-name:}") String logicalName,
            @Value("${documents.encryption.root-key-arn:}") String rootKey) {
        requireConfigured(registry);
        requireConfigured(keyStore);
        requireConfigured(logicalName);
        requireConfigured(rootKey);
        return new HierarchicalTenantCipher(dynamo, kms, keyStore, logicalName, rootKey,
                new DynamoDbTenantBranchKeyResolver(dynamo, registry), 600, "edispark-documents");
    }

    @Bean
    ContentStore documentsDeploymentContent(@Qualifier("s3Client") S3Client s3,
            @Qualifier("documentsTenantCipher") TenantCipher cipher,
            @Value("${documents.content.s3.enabled:false}") boolean enabled,
            @Value("${documents.content.s3.bucket-name:}") String bucket) {
        if (!enabled) throw new IllegalStateException("Production Documents requires encrypted S3 content storage");
        requireConfigured(bucket);
        return new S3ContentStore(s3, cipher, bucket);
    }

    private void requireConfigured(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Documents encryption/storage configuration is required");
    }
}
