package com.edispark.documents.infrastructure.storage;

import com.edispark.crypto.TenantCipher;
import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.ContentHash;
import com.edispark.documents.domain.repository.ContentStore;
import io.cottn.core.tenancy.TenantAccess;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

public final class S3ContentStore implements ContentStore {
    private final S3Client s3;
    private final TenantCipher cipher;
    private final String bucket;

    public S3ContentStore(S3Client s3, TenantCipher cipher, String bucket) {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("Documents content bucket is required");
        this.s3 = java.util.Objects.requireNonNull(s3);
        this.cipher = java.util.Objects.requireNonNull(cipher);
        this.bucket = bucket;
    }

    @Override public void store(Content content) {
        String key = ContentAddress.currentKey(content.hash());
        byte[] plaintext = ContentAddress.verify(content.hash(), content.data());
        byte[] ciphertext = cipher.encrypt(TenantAccess.currentId(), plaintext, ContentAddress.context(key));
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/octet-stream").build(),
                RequestBody.fromBytes(ciphertext));
    }

    @Override public Optional<byte[]> retrieve(ContentHash hash) {
        String key = ContentAddress.currentKey(hash);
        try {
            byte[] ciphertext = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
            return Optional.of(ContentAddress.verify(hash,
                    cipher.decrypt(TenantAccess.currentId(), ciphertext, ContentAddress.context(key))));
        } catch (NoSuchKeyException absent) {
            return Optional.empty();
        }
    }

    @Override public boolean exists(ContentHash hash) {
        String key = ContentAddress.currentKey(hash);
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) return false;
            throw failure;
        }
    }

    @Override public void delete(ContentHash hash) {
        String key = ContentAddress.currentKey(hash);
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
