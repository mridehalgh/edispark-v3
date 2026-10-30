package com.edispark.mft.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptedSecretStoreTest {
    @TempDir
    Path directory;

    @Test
    void encryptsMaterialAndEnforcesTenantBoundary() throws Exception {
        LocalEncryptedSecretStore store = new LocalEncryptedSecretStore(directory.resolve("secrets"), directory.resolve("master.key"));
        byte[] material = "not-plaintext-certificate-material".getBytes();
        String reference = store.put("tenant-a", "identity.p12", material);

        assertThat(store.get("tenant-a", reference)).isEqualTo(material);
        assertThat(Files.readAllBytes(directory.resolve("secrets").resolve(reference))).isNotEqualTo(material);
        assertThatThrownBy(() -> store.get("tenant-b", reference)).isInstanceOf(SecurityException.class);
    }
}
