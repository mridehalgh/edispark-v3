package com.edispark.crypto;

@FunctionalInterface
public interface TenantBranchKeyResolver {
    String resolve(String tenantId);
}
