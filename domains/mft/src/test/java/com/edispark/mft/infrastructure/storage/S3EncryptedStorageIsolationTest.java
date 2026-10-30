package com.edispark.mft.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.edispark.mft.testutil.AwsClientFake;
import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.TransferId;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3EncryptedStorageIsolationTest {
    @Test
    void payloadEncryptionContextAndObjectKeyAreTenantBound() throws Exception {
        RecordingCipher cipher = new RecordingCipher();
        AtomicReference<byte[]> stored = new AtomicReference<>();
        S3Client s3 = AwsClientFake.create(S3Client.class, (proxy, method, arguments) -> {
            if (method.getName().equals("putObject")) {
                RequestBody body = (RequestBody) arguments[1];
                stored.set(body.contentStreamProvider().newStream().readAllBytes());
                return PutObjectResponse.builder().build();
            }
            if (method.getName().equals("getObjectAsBytes")) {
                return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), stored.get());
            }
            throw new UnsupportedOperationException(method.getName());
        });
        S3EncryptedPayloadStore store = new S3EncryptedPayloadStore(s3, cipher, "mft-payloads");

        PayloadRef reference = store.put("tenant-a", TransferId.generate(), "orders.edi",
                new ByteArrayInputStream(new byte[]{1, 2, 3}));
        byte[] loaded = store.get("tenant-a", reference).readAllBytes();

        assertThat(loaded).containsExactly(1, 2, 3);
        assertThat(reference.key()).startsWith("tenant-a/transfers/");
        assertThat(cipher.encryptTenant).isEqualTo("tenant-a");
        assertThat(cipher.decryptTenant).isEqualTo("tenant-a");
        assertThat(cipher.context).containsEntry("purpose", "payload")
                .containsEntry("object-key", reference.key());
    }

    @Test
    void payloadReferenceFromAnotherTenantIsRejectedBeforeS3OrDecryption() {
        AtomicInteger calls = new AtomicInteger();
        S3Client s3 = AwsClientFake.create(S3Client.class, (proxy, method, arguments) -> {
            calls.incrementAndGet();
            throw new AssertionError("S3 must not be called");
        });
        RecordingCipher cipher = new RecordingCipher();
        S3EncryptedPayloadStore store = new S3EncryptedPayloadStore(s3, cipher, "mft-payloads");
        PayloadRef reference = new PayloadRef("tenant-a/transfers/id/payload", "00", 1);

        assertThatThrownBy(() -> store.get("tenant-b", reference))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Payload does not belong to the tenant");
        assertThat(calls).hasValue(0);
        assertThat(cipher.decryptTenant).isNull();
    }

    @Test
    void secretReferenceFromAnotherTenantIsRejectedBeforeS3OrDecryption() {
        AtomicInteger calls = new AtomicInteger();
        S3Client s3 = AwsClientFake.create(S3Client.class, (proxy, method, arguments) -> {
            calls.incrementAndGet();
            throw new AssertionError("S3 must not be called");
        });
        RecordingCipher cipher = new RecordingCipher();
        S3EncryptedSecretStore store = new S3EncryptedSecretStore(s3, cipher, "mft-payloads");

        assertThatThrownBy(() -> store.get("tenant-b",
                "s3://mft-payloads/tenant-a/certificate-material/id"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Secret does not belong to the tenant");
        assertThat(calls).hasValue(0);
        assertThat(cipher.decryptTenant).isNull();
    }

    private static final class RecordingCipher implements TenantCipher {
        private String encryptTenant;
        private String decryptTenant;
        private Map<String, String> context;

        @Override
        public byte[] encrypt(String tenantId, byte[] plaintext, Map<String, String> context) {
            this.encryptTenant = tenantId;
            this.context = context;
            byte[] result = Arrays.copyOf(plaintext, plaintext.length + 1);
            result[result.length - 1] = 99;
            return result;
        }

        @Override
        public byte[] decrypt(String tenantId, byte[] ciphertext, Map<String, String> context) {
            this.decryptTenant = tenantId;
            this.context = context;
            return Arrays.copyOf(ciphertext, ciphertext.length - 1);
        }
    }
}
