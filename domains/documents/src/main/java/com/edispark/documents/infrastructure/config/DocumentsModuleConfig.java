package com.edispark.documents.infrastructure.config;

import com.edispark.documents.application.handler.DocumentSetCommandHandler;
import com.edispark.documents.application.handler.SchemaCommandHandler;
import com.edispark.documents.application.query.DocumentContentQueryService;
import com.edispark.documents.application.query.DocumentSetQueryHandler;
import com.edispark.documents.application.query.DocumentWorkflowQuery;
import com.edispark.documents.application.reconciliation.DocumentReconciliation;
import com.edispark.documents.application.tradacoms.InboundTradacomsIngestionService;
import com.edispark.documents.application.tradacoms.TradacomsMessageParser;
import com.edispark.documents.application.inbound.InboundCanonicalDocumentService;
import com.edispark.documents.domain.repository.ContentStore;
import com.edispark.documents.domain.repository.DocumentSetRepository;
import com.edispark.documents.domain.repository.SchemaRepository;
import com.edispark.documents.domain.service.DocumentTransformer;
import com.edispark.documents.domain.service.DocumentValidator;
import com.edispark.documents.infrastructure.persistence.TenantScopedDocumentSetRepository;
import com.edispark.documents.infrastructure.persistence.TenantScopedSchemaRepository;
import com.edispark.documents.infrastructure.storage.FileSystemContentStore;
import com.edispark.documents.infrastructure.transformation.NoOpTransformer;
import com.edispark.documents.infrastructure.transformation.Gs1LogisticsLabelTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Spring configuration for the Documents domain module.
 * 
 * <p>This configuration provides beans for:
 * <ul>
 *   <li>AWS clients (DynamoDB, S3) with local development support</li>
 *   <li>Repository implementations</li>
 *   <li>Domain services (validators, transformers)</li>
 *   <li>Application command handlers</li>
 * </ul>
 * 
 * <p>The configuration supports both local development (using DynamoDB Local
 * and FileSystemContentStore) and production deployment (using AWS services).</p>
 */
@Configuration
public class DocumentsModuleConfig {

    @Bean
    public InboundCanonicalDocumentService inboundCanonicalDocumentService(
            DocumentSetRepository repository, ContentStore contentStore) {
        return new InboundCanonicalDocumentService(repository, contentStore);
    }

    // =============================================================================
    // AWS Client Configuration
    // =============================================================================

    /**
     * DynamoDB client for production use.
     * Uses default AWS credentials and region configuration.
     */
    @Bean
    @Profile("!local & !openapi-export")
    public DynamoDbClient dynamoDbClient(
            @Value("${aws.region:us-east-1}") String region) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Profile("openapi-export")
    @ConditionalOnMissingBean
    public DynamoDbClient openApiExportDynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:9"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("export", "export")))
                .build();
    }

    /**
     * S3 client for production use.
     * Uses default AWS credentials and region configuration.
     */
    @Bean
    @Profile("!local & !openapi-export")
    public S3Client s3Client(
            @Value("${aws.region:us-east-1}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    // =============================================================================
    // Repository Configuration
    // =============================================================================

    /**
     * DocumentSet repository implementation using DynamoDB.
     */
    @Bean
    @ConditionalOnMissingBean
    public DocumentSetRepository documentSetRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${documents.dynamodb.table-name}") String tableName) {
        return new TenantScopedDocumentSetRepository(dynamoDbClient, tableName);
    }

    /**
     * Schema repository implementation using DynamoDB.
     */
    @Bean
    @ConditionalOnMissingBean
    public SchemaRepository schemaRepository(
            DynamoDbClient dynamoDbClient,
            @Value("${documents.dynamodb.table-name}") String tableName) {
        return new TenantScopedSchemaRepository(dynamoDbClient, tableName);
    }

    /**
     * Content store implementation using local file system for development.
     * Used when S3 is not available or for local testing.
     */
    @Bean
    @Profile("local | openapi-export")
    @ConditionalOnMissingBean
    public ContentStore contentStoreLocal(
            @Value("${documents.content.local.directory:./content-store}") String directory) {
        Path contentDirectory = Paths.get(directory);
        return new FileSystemContentStore(contentDirectory);
    }

    // =============================================================================
    // Domain Service Configuration
    // =============================================================================

    /**
     * Document validator implementations.
     * Offline JSON schema validation with a fail-closed fallback for unsupported formats.
     * Add format-specific validators (XsdValidator, JsonSchemaValidator) as needed.
     */
    @Bean
    @ConditionalOnMissingBean
    public List<DocumentValidator> documentValidators() {
        return List.of(
                new com.edispark.documents.infrastructure.validation.JsonSchemaDocumentValidator(),
                new com.edispark.documents.infrastructure.validation.UnavailableDocumentValidator()
        );
    }

    /**
     * Document transformer implementations.
     * Currently provides only the NoOp transformer as a stub.
     * Add format-specific transformers (XmlToJsonTransformer, JsonToXmlTransformer) as needed.
     */
    @Bean
    @ConditionalOnMissingBean
    public List<DocumentTransformer> documentTransformers(ObjectMapper objectMapper) {
        return List.of(
                new Gs1LogisticsLabelTransformer(objectMapper),
                new NoOpTransformer()
                // TODO: Add XmlToJsonTransformer, JsonToXmlTransformer when implemented
        );
    }

    // =============================================================================
    // Application Layer Configuration
    // =============================================================================

    /**
     * Command handler for DocumentSet operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public DocumentSetCommandHandler documentSetCommandHandler(
            DocumentSetRepository documentSetRepository,
            SchemaRepository schemaRepository,
            ContentStore contentStore,
            List<DocumentValidator> validators,
            List<DocumentTransformer> transformers) {
        return new DocumentSetCommandHandler(
                documentSetRepository,
                schemaRepository,
                contentStore,
                validators,
                transformers);
    }

    /**
     * Command handler for Schema operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public SchemaCommandHandler schemaCommandHandler(
            SchemaRepository schemaRepository,
            ContentStore contentStore) {
        return new SchemaCommandHandler(schemaRepository, contentStore);
    }

    /**
     * Query handler for DocumentSet listing operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public DocumentSetQueryHandler documentSetQueryHandler(
            DocumentSetRepository documentSetRepository) {
        return new DocumentSetQueryHandler(documentSetRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentContentQueryService documentContentQueryService(
            DocumentSetRepository documentSetRepository,
            ContentStore contentStore) {
        return new DocumentContentQueryService(documentSetRepository, contentStore);
    }

    @Bean
    public DocumentWorkflowQuery documentWorkflowQuery(DocumentSetRepository sets, DocumentSetQueryHandler listing,
                                                      DocumentContentQueryService content) {
        return new DocumentWorkflowQuery(sets, listing, content);
    }

    @Bean
    public DocumentReconciliation documentReconciliation(DocumentContentQueryService content) {
        return new DocumentReconciliation(content);
    }

    @Bean
    @ConditionalOnMissingBean
    public TradacomsMessageParser tradacomsMessageParser() {
        return new TradacomsMessageParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public InboundTradacomsIngestionService inboundTradacomsIngestionService(
            DocumentSetCommandHandler commandHandler,
            TradacomsMessageParser parser) {
        return new InboundTradacomsIngestionService(commandHandler, parser);
    }
}
