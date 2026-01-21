package com.example.documents.infrastructure.persistence;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;

/**
 * Test support class for DynamoDB Local integration tests.
 * 
 * <p>This class manages the lifecycle of a DynamoDB Local server for testing purposes.</p>
 */
public final class DynamoDbLocalTestSupport {

    private DynamoDBProxyServer server;
    private DynamoDbClient client;
    private int port;

    /**
     * Starts the DynamoDB Local server on a random available port.
     */
    public void start() throws Exception {
        port = findAvailablePort();
        
        String[] localArgs = {"-inMemory", "-port", String.valueOf(port)};
        server = ServerRunner.createServerFromCommandLineArgs(localArgs);
        server.start();
        
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:" + port))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();
    }

    /**
     * Stops the DynamoDB Local server and closes the client.
     */
    public void stop() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Returns the DynamoDB client connected to the local server.
     */
    public DynamoDbClient client() {
        return client;
    }

    /**
     * Returns the port the server is running on.
     */
    public int port() {
        return port;
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
