package com.edispark.documents.infrastructure.storage;

import com.edispark.crypto.*;
import com.edispark.documents.infrastructure.persistence.*;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantExecution;
import java.nio.file.Path;
import java.util.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

/** Explicit administrative process: no Spring listeners, scheduled delivery, or runtime fallback. */
public final class DocumentsMigrationMain {
    private DocumentsMigrationMain() { }

    public static void main(String[] arguments) {
        var args = new ArrayList<>(List.of(arguments));
        boolean apply = args.remove("--apply");
        boolean schemaIndex = args.remove("--schema-index");
        if (args.size() < 2) throw new IllegalArgumentException("Usage: [--apply] [--schema-index] legacy-directory tenant-id [tenant-id ...]");
        Path legacy = Path.of(args.removeFirst()).toAbsolutePath().normalize();
        args.forEach(TenantId::fromString);
        var region = Region.of(required("AWS_REGION"));
        String table = required("DOCUMENTS_TABLE_NAME");
        if (schemaIndex) {
            try (var dynamo = DynamoDbClient.builder().region(region).build()) {
                var result = new SchemaIndexMigration(dynamo, table).run(apply);
                System.out.printf("schema-index inspected=%d changes=%d applied=%s%n", result.inspected(), result.changes(), result.applied());
            }
            return;
        }
        String bucket = required("DOCUMENTS_S3_BUCKET");
        String registry = required("DOCUMENTS_TENANT_KEY_REGISTRY_TABLE");
        String keyStore = required("DOCUMENTS_KEY_STORE_TABLE");
        String logical = required("DOCUMENTS_KEY_STORE_LOGICAL_NAME");
        String root = required("DOCUMENTS_ROOT_KMS_KEY_ARN");
        try (var dynamo = DynamoDbClient.builder().region(region).build();
             var kms = KmsClient.builder().region(region).build();
             var s3 = S3Client.builder().region(region).build()) {
            var cipher = new HierarchicalTenantCipher(dynamo, kms, keyStore, logical, root,
                    new DynamoDbTenantBranchKeyResolver(dynamo, registry), 600, "edispark-documents");
            var content = new S3ContentStore(s3, cipher, bucket);
            var migration = new LegacyContentMigration(legacy, content);
            var inventory = new RetainedContentInventory(new TenantScopedDocumentSetRepository(dynamo, table),
                    new TenantScopedSchemaRepository(dynamo, table));
            migrateTenants(args, inventory, migration, apply);
        }
    }

    private static void migrateTenants(List<String> tenants, RetainedContentInventory inventory,
                                       LegacyContentMigration migration, boolean apply) {
        Set<com.edispark.documents.domain.model.ContentHash> all = new HashSet<>();
        boolean complete = true;
        for (String tenant : tenants) {
            var hashes = TenantExecution.get(tenant, inventory::currentTenant);
            all.addAll(hashes);
            var receipts = TenantExecution.get(tenant, () -> migration.reconcile(hashes, apply));
            receipts.forEach(receipt -> System.out.printf("%s\t%s\t%s%n", receipt.tenantId(), receipt.contentHash(), receipt.status()));
            complete &= receipts.stream().noneMatch(DocumentsMigrationMain::blocksCutover);
        }
        migration.unreferenced(all).forEach(receipt -> System.out.printf("unreferenced-in-supplied-inventory\t%s%n", receipt.contentHash()));
        if (!complete) throw new IllegalStateException("Migration is blocked by missing or corrupt references; cutover is not safe");
    }

    private static boolean blocksCutover(LegacyContentMigration.Receipt receipt) {
        return receipt.status() == LegacyContentMigration.Status.MISSING
                || receipt.status() == LegacyContentMigration.Status.CORRUPT;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required configuration: " + name);
        return value;
    }
}
