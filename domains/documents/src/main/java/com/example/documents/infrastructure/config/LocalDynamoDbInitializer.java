package com.example.documents.infrastructure.config;

import com.example.documents.infrastructure.persistence.DynamoDbTableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Initializes DynamoDB table for local development.
 * 
 * <p>This component runs after the application context is ready and creates
 * the DynamoDB table if it doesn't exist. It runs before the seeder.</p>
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalDynamoDbInitializer {

    private final DynamoDbClient dynamoDbClient;
    
    @Value("${documents.dynamodb.table-name:myapp-documents-dev}")
    private String tableName;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void initializeTable() {
        log.info("Initializing DynamoDB table: {}", tableName);
        
        DynamoDbTableConfig tableConfig = new DynamoDbTableConfig(tableName);
        tableConfig.createTableIfNotExists(dynamoDbClient);
        
        log.info("DynamoDB table ready: {}", tableName);
    }
}
