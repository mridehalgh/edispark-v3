package com.edispark.mft.infrastructure.config;

import com.edispark.mft.application.CertificateService;
import com.edispark.mft.application.ConnectionDetailsService;
import com.edispark.mft.application.MftService;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.PayloadStore;
import com.edispark.mft.domain.repository.SecretStore;
import com.edispark.mft.infrastructure.persistence.DynamoDbMftRepository;
import com.edispark.mft.infrastructure.storage.AwsHierarchicalKeyringTenantCipher;
import com.edispark.mft.infrastructure.storage.DynamoDbTenantBranchKeyResolver;
import com.edispark.mft.infrastructure.storage.S3EncryptedPayloadStore;
import com.edispark.mft.infrastructure.storage.S3EncryptedSecretStore;
import com.edispark.mft.infrastructure.storage.TenantBranchKeyResolver;
import com.edispark.mft.infrastructure.storage.TenantCipher;
import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("production")
public class ProductionMftModuleConfig {
    @Bean
    DynamoDbClient mftDynamoDbClient(@Value("${mft.aws.region}") String region) {
        return DynamoDbClient.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    @Bean
    S3Client mftS3Client(@Value("${mft.aws.region}") String region) {
        return S3Client.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    @Bean
    KmsClient mftKmsClient(@Value("${mft.aws.region}") String region) {
        return KmsClient.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    @Bean
    MftRepository mftRepository(DynamoDbClient dynamoDb,
            @Value("${mft.aws.metadata-table}") String tableName) {
        return new DynamoDbMftRepository(dynamoDb, tableName);
    }

    @Bean
    TenantBranchKeyResolver mftTenantBranchKeyResolver(DynamoDbClient dynamoDb,
            @Value("${mft.aws.metadata-table}") String tableName) {
        return new DynamoDbTenantBranchKeyResolver(dynamoDb, tableName);
    }

    @Bean
    TenantCipher mftTenantCipher(DynamoDbClient dynamoDb, KmsClient kms, TenantBranchKeyResolver branchKeys,
            @Value("${mft.aws.key-store-table}") String keyStoreTable,
            @Value("${mft.aws.key-store-logical-name}") String logicalName,
            @Value("${mft.aws.root-kms-key-arn}") String rootKeyArn,
            @Value("${mft.aws.branch-key-cache-ttl-seconds:600}") long cacheTtlSeconds) {
        return new AwsHierarchicalKeyringTenantCipher(dynamoDb, kms, keyStoreTable, logicalName,
                rootKeyArn, branchKeys, cacheTtlSeconds);
    }

    @Bean
    PayloadStore mftPayloadStore(S3Client s3, TenantCipher cipher,
            @Value("${mft.aws.payload-bucket}") String bucket) {
        return new S3EncryptedPayloadStore(s3, cipher, bucket);
    }

    @Bean
    SecretStore mftSecretStore(S3Client s3, TenantCipher cipher,
            @Value("${mft.aws.payload-bucket}") String bucket) {
        return new S3EncryptedSecretStore(s3, cipher, bucket);
    }

    @Bean
    Clock mftClock() {
        return Clock.systemUTC();
    }

    @Bean
    CertificateService mftCertificateService(MftRepository repository, SecretStore secretStore, Clock clock) {
        return new CertificateService(repository, secretStore, clock, false);
    }

    @Bean
    ConnectionDetailsService mftConnectionDetailsService(MftRepository repository,
            @Value("${mft.as2.public-inbound-url}") URI inboundUrl,
            @Value("${mft.as2.public-async-mdn-url}") URI asynchronousMdnUrl,
            @Value("${mft.as2.supported-encryption-algorithms:AES128-GCM}") String encryptionAlgorithms,
            @Value("${mft.as2.supported-signature-algorithms:SHA-256}") String signatureAlgorithms,
            @Value("${mft.as2.key-encryption-algorithm:RSA-OAEP}") String keyEncryptionAlgorithm) {
        return new ConnectionDetailsService(repository, inboundUrl, asynchronousMdnUrl,
                commaSeparated(encryptionAlgorithms), commaSeparated(signatureAlgorithms), keyEncryptionAlgorithm);
    }

    @Bean
    MftService mftService(MftRepository repository, PayloadStore payloadStore, Clock clock,
            @Value("${mft.as2.allow-legacy-crypto:false}") boolean allowLegacyCrypto) {
        return new MftService(repository, payloadStore, clock, allowLegacyCrypto);
    }

    private List<String> commaSeparated(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
