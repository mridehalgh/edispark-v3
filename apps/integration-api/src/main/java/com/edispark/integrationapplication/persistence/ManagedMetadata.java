package com.edispark.integrationapplication.persistence;

import io.cottn.core.tenancy.TenantAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!openapi-export")
public final class ManagedMetadata {
    private static final String CATALOGUE = "__MANAGED_CATALOGUE__";
    private final IntegrationDynamoStore store;

    ManagedMetadata(IntegrationDynamoStore store) {
        this.store = store;
    }

    public List<String> catalogue() {
        return store.list(CATALOGUE, "TEMPLATE#");
    }

    public Optional<String> template(String key) {
        return store.get(CATALOGUE, "TEMPLATE#" + key);
    }

    public boolean writeTemplate(String key, String json, long version) {
        return store.putVersionedIfCurrent(CATALOGUE, "TEMPLATE#" + key, "MANAGED_TEMPLATE", json, version);
    }

    public Optional<String> setup(String key) {
        return store.get(TenantAccess.currentId(), "MANAGED_SETUP#" + key);
    }

    public boolean reserveSetup(String key, String json, String agreementFamily) {
        return store.putPairIfAbsent(TenantAccess.currentId(), "MANAGED_SETUP#" + key, "MANAGED_SETUP", json,
                "MANAGED_AGREEMENT#" + agreementFamily, json);
    }

    public Optional<String> agreement(String key) {
        return store.get(TenantAccess.currentId(), "MANAGED_AGREEMENT#" + key);
    }

    public Optional<String> acceptance(String key) {
        return store.get(TenantAccess.currentId(), "MANAGED_ACCEPTANCE#" + key);
    }

    public void recordAcceptance(String key, String json) {
        store.put(TenantAccess.currentId(), "MANAGED_ACCEPTANCE#" + key, "MANAGED_ACCEPTANCE", json);
    }
}
