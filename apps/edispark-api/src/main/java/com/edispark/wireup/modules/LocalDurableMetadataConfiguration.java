package com.edispark.wireup.modules;

import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.infrastructure.persistence.DynamoDbMftRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** Local metadata uses the same repositories as deployment and the existing durable embedded database. */
@Configuration(proxyBeanMethods = false)
@Profile("local & !openapi-export")
class LocalDurableMetadataConfiguration {
    @Bean static BeanFactoryPostProcessor integrationLocalDatabaseAlias() {
        return factory -> {
            String[] clients = factory.getBeanNamesForType(DynamoDbClient.class, false, false);
            if (clients.length != 1) throw new IllegalStateException("Local metadata needs exactly one local DynamoDB client");
            factory.registerAlias(clients[0], "integrationDynamoDb");
        };
    }

    @Bean LocalMetadataTables localMetadataTables(DynamoDbClient client,
            @Value("${integration.aws.metadata-table}") String integration,
            @Value("${mft.local.metadata-table}") String mft,
            @Value("${edispark.agent.usagetable:edispark-desk-usage-local}") String deskUsage) {
        ensureTable(client, integration, false);
        ensureTable(client, mft, true);
        ensureTable(client, deskUsage, false);
        return new LocalMetadataTables(mft);
    }

    @Bean @Primary MftRepository localDurableMftRepository(DynamoDbClient client, LocalMetadataTables tables) {
        return new DynamoDbMftRepository(client, tables.mftTable());
    }

    private void ensureTable(DynamoDbClient client, String name, boolean workIndex) {
        try { client.describeTable(request -> request.tableName(name)); return; }
        catch (ResourceNotFoundException expected) { /* Create only this named local table. */ }
        List<AttributeDefinition> attributes = new ArrayList<>(List.of(attribute("pk"), attribute("sk")));
        var request = CreateTableRequest.builder().tableName(name).billingMode(BillingMode.PAY_PER_REQUEST)
                .keySchema(key("pk", KeyType.HASH), key("sk", KeyType.RANGE));
        if (workIndex) {
            attributes.add(attribute("workPk")); attributes.add(attribute("workSk"));
            request.globalSecondaryIndexes(GlobalSecondaryIndex.builder().indexName("work-index")
                    .keySchema(key("workPk", KeyType.HASH), key("workSk", KeyType.RANGE))
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build()).build());
        }
        client.createTable(request.attributeDefinitions(attributes).build());
        client.waiter().waitUntilTableExists(describe -> describe.tableName(name));
    }

    private AttributeDefinition attribute(String name) { return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build(); }
    private KeySchemaElement key(String name, KeyType type) { return KeySchemaElement.builder().attributeName(name).keyType(type).build(); }
    record LocalMetadataTables(String mftTable) { }
}
