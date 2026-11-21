package com.edispark.documents.infrastructure.storage;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.Format;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TenantContentIsolationTest {
    @TempDir Path directory;

    @Test void identicalContentHasIndependentOwnershipAndDeletion() {
        var store = new FileSystemContentStore(directory);
        var content = Content.of("[document content]".getBytes(), Format.JSON);
        String a = TenantId.newTenantId().asString();
        String b = TenantId.newTenantId().asString();
        assertThatThrownBy(() -> store.store(content)).isInstanceOf(NoTenantSetException.class);
        TenantExecution.run(a, () -> store.store(content));
        TenantExecution.run(b, () -> {
            assertThat(store.retrieve(content.hash())).isEmpty();
            assertThat(store.exists(content.hash())).isFalse();
            store.delete(content.hash());
            store.store(content);
        });
        TenantExecution.run(a, () -> {
            assertThat(store.retrieve(content.hash())).hasValue(content.data());
            store.delete(content.hash());
            assertThat(store.exists(content.hash())).isFalse();
        });
        TenantExecution.run(b, () -> assertThat(store.retrieve(content.hash())).hasValue(content.data()));
        assertThatThrownBy(() -> store.retrieve(content.hash())).isInstanceOf(NoTenantSetException.class);
    }

    @Test void tamperedCiphertextFailsAuthentication() throws Exception {
        var store = new FileSystemContentStore(directory);
        String tenant = TenantId.newTenantId().asString();
        var content = Content.of("[document content]".getBytes(), Format.JSON);
        TenantExecution.run(tenant, () -> store.store(content));
        Path blob;
        try (var files = Files.walk(directory)) {
            blob = files.filter(path -> path.toString().endsWith(".enc")).findFirst().orElseThrow();
        }
        byte[] encrypted = Files.readAllBytes(blob);
        assertThat(new String(encrypted)).doesNotContain("[document content]");
        encrypted[encrypted.length - 1] ^= 1;
        Files.write(blob, encrypted);
        TenantExecution.run(tenant, () -> assertThatThrownBy(() -> store.retrieve(content.hash()))
                .isInstanceOf(SecurityException.class));
    }
}
