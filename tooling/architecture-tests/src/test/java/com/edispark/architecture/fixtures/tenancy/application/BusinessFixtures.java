package com.edispark.architecture.fixtures.tenancy.application;

import com.edispark.architecture.fixtures.tenancy.domain.repository.PortFixtures;
import com.edispark.architecture.fixtures.tenancy.infrastructure.TrustedIngress;
import io.cottn.core.tenancy.TenantContext;
import software.amazon.awssdk.services.s3.S3Client;

public final class BusinessFixtures {
    public static class ReadsContext {
        public String tenant() { return TenantContext.getCurrentTenant().asString(); }
    }

    public static class RebindsContext {
        public void process() { TenantContext.runWhere(null, () -> { }); }
    }

    public record UsesRawStorage(S3Client storage) { }

    public record UsesConcretePersistence(TrustedIngress.Persistence repository) { }

    public record CallsGlobalDiscovery(PortFixtures.GlobalRepository repository) {
        public void process() { repository.findInboundPartner("local", "remote"); }
    }

    public record CallsStringSelector(PortFixtures.StringRepository repository) {
        public void process(String selector) { repository.find(selector, java.util.UUID.randomUUID()); }
    }

    public record ReferencesTypedSelector(PortFixtures.TypedRepository repository) {
        public java.util.function.BiConsumer<com.edispark.identifiers.tenant.TenantId, java.util.UUID> selector() {
            return repository::find;
        }
    }

    public record ScopedBusinessService(PortFixtures.ScopedRepository repository) {
        public void process() { repository.find(java.util.UUID.randomUUID()); }
    }
}
