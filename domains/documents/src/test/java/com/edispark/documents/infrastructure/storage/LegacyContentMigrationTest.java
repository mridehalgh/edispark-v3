package com.edispark.documents.infrastructure.storage;

import com.edispark.documents.domain.model.*;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantExecution;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LegacyContentMigrationTest {
    @TempDir Path root;

    @Test void dryRunCopyReplayAndSharedOwnershipRetainTheOriginal() throws Exception {
        var content = Content.of("[legacy bytes]".getBytes(), Format.JSON);
        Path original = root.resolve(ContentAddress.filename(content.hash()));
        Files.write(original, content.data());
        var destination = new FileSystemContentStore(root);
        var migration = new LegacyContentMigration(root, destination);
        for (int index = 0; index < 2; index++) {
            String owner = TenantId.newTenantId().asString();
            TenantExecution.run(owner, () -> {
                assertThat(migration.reconcile(List.of(content.hash()), false).getFirst().status())
                        .isEqualTo(LegacyContentMigration.Status.WOULD_COPY);
                assertThat(destination.retrieve(content.hash())).isEmpty();
                assertThat(migration.reconcile(List.of(content.hash()), true).getFirst().status())
                        .isEqualTo(LegacyContentMigration.Status.COPIED);
                assertThat(migration.reconcile(List.of(content.hash()), true).getFirst().status())
                        .isEqualTo(LegacyContentMigration.Status.VERIFIED);
                assertThat(destination.retrieve(content.hash())).hasValue(content.data());
            });
        }
        assertThat(Files.readAllBytes(original)).isEqualTo(content.data());
        assertThat(migration.unreferenced(List.of())).singleElement()
                .extracting(LegacyContentMigration.Receipt::status).isEqualTo(LegacyContentMigration.Status.UNREFERENCED);
    }

    @Test void missingAndCorruptReferencesBlockCopyWithoutChangingLegacyData() throws Exception {
        var content = Content.of("[expected bytes]".getBytes(), Format.JSON);
        var migration = new LegacyContentMigration(root, new FileSystemContentStore(root));
        String owner = TenantId.newTenantId().asString();
        TenantExecution.run(owner, () -> assertThat(migration.reconcile(List.of(content.hash()), true).getFirst().status())
                .isEqualTo(LegacyContentMigration.Status.MISSING));
        Files.write(root.resolve(ContentAddress.filename(content.hash())), new byte[]{0});
        TenantExecution.run(owner, () -> assertThat(migration.reconcile(List.of(content.hash()), true).getFirst().status())
                .isEqualTo(LegacyContentMigration.Status.CORRUPT));
    }
}
