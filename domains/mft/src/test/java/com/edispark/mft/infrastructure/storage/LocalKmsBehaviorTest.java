package com.edispark.mft.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

class LocalKmsBehaviorTest {
    @Test
    void branchKeysAndEncryptionContextIsolateLocalCiphertext() throws Exception {
        LocalKms kms = new LocalKms(Files.createTempDirectory("local-kms").resolve("root.key"));
        byte[] nonce = new byte[12];
        LocalKms.Context context = new LocalKms.Context("tenant-a", "payload", "transfer-1");
        byte[] ciphertext = kms.cipher(Cipher.ENCRYPT_MODE, nonce, context).doFinal("EDI".getBytes());

        assertThat(kms.rootKeyAlias()).isEqualTo("alias/edispark/local/hierarchical-root");
        assertThat(kms.branchKeyId("tenant-a")).isEqualTo("tenant/tenant-a");
        assertThat(kms.cipher(Cipher.DECRYPT_MODE, nonce, context).doFinal(ciphertext)).isEqualTo("EDI".getBytes());
        assertThatThrownBy(() -> kms.cipher(Cipher.DECRYPT_MODE, nonce,
                new LocalKms.Context("tenant-b", "payload", "transfer-1")).doFinal(ciphertext))
                .isInstanceOf(AEADBadTagException.class);
        assertThatThrownBy(() -> kms.cipher(Cipher.DECRYPT_MODE, nonce,
                new LocalKms.Context("tenant-a", "payload", "transfer-2")).doFinal(ciphertext))
                .isInstanceOf(AEADBadTagException.class);
    }
}
