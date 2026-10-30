package com.edispark.mft.infrastructure.storage;

@FunctionalInterface
public interface TenantBranchKeyResolver {
    String resolve(String tenantId);
}
