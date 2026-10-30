package com.edispark.mft.infrastructure.storage;

import com.edispark.mft.domain.repository.SecretStore;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public final class S3EncryptedSecretStore implements SecretStore {
    private final S3Client s3;
    private final TenantCipher cipher;
    private final String bucket;

    public S3EncryptedSecretStore(S3Client s3, TenantCipher cipher, String bucket) {
        this.s3 = s3;
        this.cipher = cipher;
        this.bucket = bucket;
    }

    @Override
    public String put(String tenantId, String name, byte[] plaintext) {
        String key = tenantId + "/certificate-material/" + UUID.randomUUID();
        byte[] ciphertext = cipher.encrypt(tenantId, plaintext, context(key));
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                        .contentType("application/octet-stream")
                        .metadata(Map.of("edispark-format", "esdk-v1"))
                        .build(), RequestBody.fromBytes(ciphertext));
        return "s3://" + bucket + "/" + key;
    }

    @Override
    public byte[] get(String tenantId, String reference) throws IOException {
        String prefix = "s3://" + bucket + "/" + tenantId + "/";
        if (!reference.startsWith(prefix)) throw new SecurityException("Secret does not belong to the tenant");
        String key = reference.substring(("s3://" + bucket + "/").length());
        try {
            byte[] ciphertext = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray();
            return cipher.decrypt(tenantId, ciphertext, context(key));
        } catch (RuntimeException exception) {
            throw new IOException("Certificate material could not be read", exception);
        }
    }

    private Map<String, String> context(String key) {
        return Map.of("object-key", key, "purpose", "certificate-material");
    }
}
