package com.edispark.integrationapplication.seed;

import com.edispark.integrationapplication.persistence.ReferenceTables;
import com.edispark.transformations.domain.ReferenceTableRevision;
import io.cottn.core.tenancy.TenantExecution;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("local & !openapi-export")
final class LocalReferenceTableFixture {
    private static final UUID SITES = UUID.fromString("8c61797d-eabe-4fee-8f52-57c28b11c03f");
    private final ReferenceTables tables;

    LocalReferenceTableFixture(ReferenceTables tables) {
        this.tables = tables;
    }

    @EventListener
    void reconcile(LocalIntegrationDemoSeeder.Ready ready) {
        TenantExecution.run(ready.tenantId(), () -> tables.publish(new ReferenceTableRevision(
                SITES, null, "Demo delivery sites", "siteCode", List.of(
                        Map.of("siteCode", "STORE-042", "erpSite", "SITE-001", "street", "[address]", "countryCode", "GB"),
                        Map.of("siteCode", "0007", "erpSite", "SITE-002", "street", "[address]", "countryCode", "GB")))));
    }
}
