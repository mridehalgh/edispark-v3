package com.edispark.documents.infrastructure.storage;

import com.edispark.documents.domain.model.Content;
import com.edispark.documents.domain.model.ContentHash;
import io.cottn.core.tenancy.TenantAccess;
import java.util.Map;

final class ContentAddress {
    private ContentAddress() { }

    static String filename(ContentHash hash) {
        if (!hash.algorithm().equalsIgnoreCase("SHA-256") || !hash.hash().matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("A canonical SHA-256 content hash is required");
        }
        return "sha-256_" + hash.hash();
    }

    static String currentKey(ContentHash hash) {
        return "tenants/" + TenantAccess.currentId() + "/content/" + filename(hash) + ".enc";
    }

    static Map<String, String> context(String key) {
        return Map.of("resource_type", "document-content", "resource_id", key);
    }

    static byte[] verify(ContentHash expected, byte[] plaintext) {
        if (!Content.computeHash(plaintext).equals(expected)) throw new SecurityException("Content integrity check failed");
        return plaintext;
    }
}
