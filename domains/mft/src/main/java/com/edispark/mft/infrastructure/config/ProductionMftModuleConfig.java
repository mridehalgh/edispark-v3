package com.edispark.mft.infrastructure.config;

import com.edispark.mft.application.CertificateService;
import com.edispark.mft.application.ConnectionDetailsService;
import com.edispark.mft.application.MftService;
import com.edispark.mft.application.As2ExchangePort;
import com.edispark.mft.application.As2EncryptionCertificateProvider;
import com.edispark.mft.application.MdnService;
import com.edispark.mft.application.MftDeliveryService;
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
import com.edispark.mft.infrastructure.as2.As2EndpointPolicy;
import com.edispark.mft.infrastructure.as2.KmsAs2ExchangeAdapter;
import com.edispark.mft.infrastructure.as2.KmsAs2Identity;
import com.edispark.mft.infrastructure.as2.KmsAs2InboundService;
import com.edispark.mft.infrastructure.as2.KmsCmsCodec;
import com.edispark.mft.infrastructure.as2.MftDeliveryWorker;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
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
    CertificateService mftCertificateService(MftRepository repository, SecretStore secretStore, Clock clock,
                                             KmsAs2Identity identity) {
        try {
            return new CertificateService(repository, secretStore, clock,
                    identity.signingCertificate().getEncoded());
        } catch (java.security.cert.CertificateEncodingException exception) {
            throw new IllegalStateException("Unable to encode managed AS2 signing certificate", exception);
        }
    }

    @Bean
    ConnectionDetailsService mftConnectionDetailsService(MftRepository repository, KmsAs2Identity identity,
            @Value("${mft.as2.public-inbound-url}") URI inboundUrl,
            @Value("${mft.as2.public-async-mdn-url}") URI asynchronousMdnUrl,
            @Value("${mft.as2.supported-encryption-algorithms:AES256-CBC}") String encryptionAlgorithms,
            @Value("${mft.as2.supported-signature-algorithms:SHA-256}") String signatureAlgorithms,
            @Value("${mft.as2.key-encryption-algorithm:RSA-OAEP-SHA256}") String keyEncryptionAlgorithm) {
        return new ConnectionDetailsService(repository, inboundUrl, asynchronousMdnUrl,
                commaSeparated(encryptionAlgorithms), commaSeparated(signatureAlgorithms), keyEncryptionAlgorithm,
                certificate -> publicCertificate("AS2 encryption", identity.encryptionCertificate()));
    }

    @Bean
    MftService mftService(MftRepository repository, PayloadStore payloadStore, Clock clock,
            @Value("${mft.as2.allow-legacy-crypto:false}") boolean allowLegacyCrypto) {
        return new MftService(repository, payloadStore, clock, allowLegacyCrypto);
    }

    @Bean
    KmsAs2Identity mftKmsAs2Identity(KmsClient kms, Clock clock,
            @Value("${mft.as2.kms.signing-key-arn}") String signingKeyArn,
            @Value("${mft.as2.kms.decryption-key-arn}") String decryptionKeyArn,
            @Value("${mft.as2.kms.signing-certificate:}") String signingCertificate,
            @Value("${mft.as2.kms.encryption-certificate:}") String encryptionCertificate,
            @Value("${mft.as2.kms.signing-certificate-base64:}") String signingCertificateBase64,
            @Value("${mft.as2.kms.encryption-certificate-base64:}") String encryptionCertificateBase64,
            ResourceLoader resources) throws IOException {
        return KmsAs2Identity.load(kms, signingKeyArn, decryptionKeyArn,
                certificateBytes(signingCertificateBase64, signingCertificate, resources, "signing"),
                certificateBytes(encryptionCertificateBase64, encryptionCertificate, resources, "encryption"), clock);
    }

    @Bean
    KmsCmsCodec mftKmsCmsCodec(KmsClient kms, KmsAs2Identity identity) {
        return new KmsCmsCodec(kms, identity.signingKeyId(), identity.decryptionKeyId(),
                identity.signingCertificate());
    }

    @Bean
    As2EndpointPolicy mftAs2EndpointPolicy(
            @Value("${mft.as2.allow-private-networks:false}") boolean allowPrivateNetworks) {
        return new As2EndpointPolicy(allowPrivateNetworks);
    }

    @Bean
    As2ExchangePort mftAs2ExchangePort(CertificateService certificates, KmsAs2Identity identity,
            KmsCmsCodec cms, As2EndpointPolicy endpoints,
            @Value("${mft.as2.public-async-mdn-url}") URI asynchronousMdnUrl, Clock clock) {
        return new KmsAs2ExchangeAdapter(certificates, identity, cms, endpoints, asynchronousMdnUrl, clock, repository);
    }

    @Bean
    MdnService mftMdnService(MftRepository repository, Clock clock) {
        return new MdnService(repository, clock);
    }

    @Bean
    KmsAs2InboundService mftKmsAs2InboundService(MftRepository repository,
            CertificateService certificates, MftService transfers, MdnService mdns,
            KmsCmsCodec cms, As2EndpointPolicy endpoints) {
        return new KmsAs2InboundService(repository, certificates, transfers, mdns, cms, endpoints);
    }

    @Bean
    MftDeliveryService mftDeliveryService(MftRepository repository, PayloadStore payloadStore,
            As2ExchangePort exchange, Clock clock) {
        return new MftDeliveryService(repository, payloadStore, exchange, clock);
    }

    @Bean
    MftDeliveryWorker mftDeliveryWorker(MftRepository repository, MftDeliveryService deliveryService,
                                        Clock clock) {
        return new MftDeliveryWorker(repository, deliveryService, clock);
    }

    private List<String> commaSeparated(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private byte[] certificateBytes(String base64, String location, ResourceLoader resources,
                                    String purpose) throws IOException {
        if (base64 != null && !base64.isBlank()) {
            try {
                return java.util.Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("AS2 " + purpose + " certificate base64 is invalid", exception);
            }
        }
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("AS2 " + purpose + " certificate is required");
        }
        return resources.getResource(location).getInputStream().readAllBytes();
    }

    private As2EncryptionCertificateProvider.PublicCertificate publicCertificate(
            String name, java.security.cert.X509Certificate certificate) {
        try {
            String fingerprint = java.util.HexFormat.ofDelimiter(":").withUpperCase().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            String encoded = java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                    .encodeToString(certificate.getEncoded());
            return new As2EncryptionCertificateProvider.PublicCertificate(name, fingerprint,
                    certificate.getNotAfter().toInstant(),
                    "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode AS2 encryption certificate", exception);
        }
    }
}
