package com.edispark.mft.domain.repository;

import java.io.IOException;

public interface SecretStore {
    String put(String tenantId, String name, byte[] plaintext) throws IOException;
    byte[] get(String tenantId, String reference) throws IOException;
}
