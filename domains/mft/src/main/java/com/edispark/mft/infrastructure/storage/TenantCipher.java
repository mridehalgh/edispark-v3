package com.edispark.mft.infrastructure.storage;

import java.util.Map;

public interface TenantCipher {
    byte[] encrypt(String tenantId, byte[] plaintext, Map<String, String> context);
    byte[] decrypt(String tenantId, byte[] ciphertext, Map<String, String> context);
}
