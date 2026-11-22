package com.edispark.wireup.mig;

import com.edispark.integrationapplication.seed.LocalIntegrationDemoSeeder;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("local & !production & !openapi-export")
@RequiredArgsConstructor
final class MigDemoSeeder {
    private final MigWorkflow workflow;

    @EventListener
    void seed(LocalIntegrationDemoSeeder.Ready ready) {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(ready.tenantId())), () -> {
            try { workflow.seedDemo(); }
            catch (IOException invalid) { throw new UncheckedIOException(invalid); }
        });
    }
}
