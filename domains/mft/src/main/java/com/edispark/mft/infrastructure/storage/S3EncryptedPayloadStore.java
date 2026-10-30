package com.edispark.mft.infrastructure.storage;

import com.edispark.mft.domain.model.PayloadRef;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.repository.PayloadStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public final class S3EncryptedPayloadStore implements PayloadStore {
    private final S3Client s3;
    private final TenantCipher cipher;
    private final String bucket;

    public S3EncryptedPayloadStore(S3Client s3, TenantCipher cipher, String bucket) {
        this.s3 = s3;
        this.cipher = cipher;
        this.bucket = bucket;
    }

    @Override
    public PayloadRef put(String tenantId, TransferId transferId, String filename, InputStream content) throws IOException {
        byte[] plaintext = content.readAllBytes();
        String key = tenantId + "/transfers/" + transferId + "/payload";
        Map<String, String> context = context(key, "payload");
        byte[] ciphertext = cipher.encrypt(tenantId, plaintext, context);
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                        .contentType("application/octet-stream")
                        .metadata(Map.of("edispark-sha256", sha256(plaintext), "edispark-format", "esdk-v1"))
                        .build(), RequestBody.fromBytes(ciphertext));
        return new PayloadRef(key, sha256(plaintext), plaintext.length);
    }

    @Override
    public InputStream get(String tenantId, PayloadRef payload) throws IOException {
        requireTenantPrefix(tenantId, payload.key());
        try {
            byte[] ciphertext = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(payload.key()).build())
                    .asByteArray();
            byte[] plaintext = cipher.decrypt(tenantId, ciphertext, context(payload.key(), "payload"));
            if (plaintext.length != payload.size() || !sha256(plaintext).equals(payload.sha256())) {
                throw new IOException("Payload integrity check failed");
            }
            return new ByteArrayInputStream(plaintext);
        } catch (NoSuchKeyException exception) {
            throw new IOException("Payload was not found", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Payload could not be decrypted", exception);
        }
    }

    private Map<String, String> context(String key, String purpose) {
        return Map.of("object-key", key, "purpose", purpose);
    }

    private void requireTenantPrefix(String tenantId, String key) {
        if (!key.startsWith(tenantId + "/")) throw new SecurityException("Payload does not belong to the tenant");
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
