package com.edispark.mft.infrastructure.storage;

import io.cottn.core.tenancy.TenantAccess;

import java.io.IOException;

import com.edispark.mft.domain.repository.SecretStore;
public interface ExplicitSecretStore extends SecretStore {
    String put(String tenantId, String name, byte[] plaintext) throws IOException;
    byte[] get(String tenantId, String reference) throws IOException;
    default void delete(String tenantId, String reference) throws IOException { throw new IOException("Secret deletion is not supported"); }
    default String put(String name, byte[] plaintext) throws IOException { return put(TenantAccess.currentId(), name, plaintext); }
    default byte[] get(String reference) throws IOException { return get(TenantAccess.currentId(), reference); }
    default void delete(String reference) throws IOException { delete(TenantAccess.currentId(), reference); }
}
