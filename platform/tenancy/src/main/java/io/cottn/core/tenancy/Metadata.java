package io.cottn.core.tenancy;

import com.edispark.identifiers.tenant.TenantId;

public record Metadata(TenantId tenantId) {
  public Metadata {
    if (tenantId == null) {
      tenantId = TenantContext.getCurrentTenant();
    }
  }
}
