package com.edispark.localruntime.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import java.net.URI;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Slf4j
@AutoConfiguration
@Profile("local & !openapi-export")
@EnableConfigurationProperties(LocalDynamoDbProperties.class)
@ConditionalOnProperty(name = "dynamodb.local.embedded", havingValue = "true", matchIfMissing = true)
public class EmbeddedDynamoDbAutoConfiguration implements DisposableBean {

    private DynamoDBProxyServer server;

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbClient localDynamoDbClient(LocalDynamoDbProperties properties) throws Exception {
        URI endpoint = URI.create(properties.getEndpoint());
        startServer(properties, endpoint.getPort());
        return DynamoDbClient.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
    }

    private void startServer(LocalDynamoDbProperties properties, int port) throws Exception {
        Files.createDirectories(properties.getDataDirectory());
        System.setProperty(
                "sqlite4java.library.path",
                properties.getNativeLibraryPath().toAbsolutePath().normalize().toString());

        String[] arguments = {
            "-sharedDb",
            "-dbPath", properties.getDataDirectory().toAbsolutePath().normalize().toString(),
            "-port", Integer.toString(port),
            "-disableTelemetry"
        };
        server = ServerRunner.createServerFromCommandLineArgs(arguments);
        server.start();
        log.info("Embedded DynamoDB is ready at {}", properties.getEndpoint());
    }

    @Override
    public void destroy() throws Exception {
        if (server == null) {
            return;
        }
        server.stop();
        log.info("Embedded DynamoDB stopped");
    }
}
