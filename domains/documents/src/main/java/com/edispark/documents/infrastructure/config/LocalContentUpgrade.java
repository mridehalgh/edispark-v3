package com.edispark.documents.infrastructure.config;

import com.edispark.documents.domain.repository.*;
import com.edispark.documents.infrastructure.persistence.SchemaIndexMigration;
import com.edispark.documents.infrastructure.storage.*;
import io.cottn.core.tenancy.TenantExecution;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Component
@Profile("local & !openapi-export")
public record LocalContentUpgrade(DynamoDbClient dynamoDbClient, DocumentSetRepository documents,
        SchemaRepository schemas, ContentStore content,
        @Value("${documents.dynamodb.table-name}") String table,
        @Value("${documents.content.local.directory}") String directory,
        @Value("${documents.seed.tenant-id}") String tenant) {
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public void migrateRetainedLocalContent() {
        new SchemaIndexMigration(dynamoDbClient, table).run(true);
        TenantExecution.run(tenant, () -> {
            var hashes = new RetainedContentInventory(documents, schemas).currentTenant();
            var receipts = new LegacyContentMigration(Path.of(directory), content).reconcile(hashes, true);
            long blocked = receipts.stream().filter(receipt -> receipt.status() == LegacyContentMigration.Status.CORRUPT
                    || receipt.status() == LegacyContentMigration.Status.MISSING).count();
            org.slf4j.LoggerFactory.getLogger(LocalContentUpgrade.class).info(
                    "Local content upgrade completed references={} unavailable={} legacyFilesRetained=true", receipts.size(), blocked);
        });
    }
}
