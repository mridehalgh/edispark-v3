package com.example.documents.infrastructure.config;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * Starts an embedded DynamoDB Local server for local development.
 * 
 * <p>This configuration is only active when:
 * <ul>
 *   <li>Profile is "local"</li>
 *   <li>Property dynamodb.local.embedded is true (default in local profile)</li>
 * </ul>
 * 
 * <p>The embedded server is started before the DynamoDB client bean is created,
 * ensuring proper startup order. Table creation is handled separately by
 * {@link LocalDynamoDbInitializer}.</p>
 */
@Configuration
@Profile("local")
@ConditionalOnProperty(
    name = "dynamodb.local.embedded",
    havingValue = "true",
    matchIfMissing = true
)
@Slf4j
public class EmbeddedDynamoDbConfig {

    private static final String DEFAULT_ENDPOINT = "http://localhost:8000";
    private DynamoDBProxyServer server;
    private boolean serverStarted = false;

    /**
     * Creates a DynamoDB client after starting the embedded server.
     * This bean is marked as @Primary to override the one in DocumentsModuleConfig.
     */
    @Bean
    @Primary
    public DynamoDbClient dynamoDbClient() throws Exception {
        ensureServerStarted();
        return createClient();
    }
    
    private synchronized void ensureServerStarted() throws Exception {
        if (serverStarted) {
            return;
        }
        
        System.setProperty("sqlite4java.library.path", "target/dependencies");
        
        log.info("Starting embedded DynamoDB Local on port 8000...");
        
        String[] localArgs = {"-inMemory", "-port", "8000"};
        server = ServerRunner.createServerFromCommandLineArgs(localArgs);
        server.start();
        serverStarted = true;
        
        log.info("Embedded DynamoDB Local started successfully");
    }
    
    private DynamoDbClient createClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(DEFAULT_ENDPOINT))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();
    }

    @PreDestroy
    public void stopDynamoDbLocal() throws Exception {
        if (server != null) {
            log.info("Stopping embedded DynamoDB Local...");
            server.stop();
            log.info("Embedded DynamoDB Local stopped");
        }
    }
}
