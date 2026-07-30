package io.cottn.core.tenancy;

import com.edispark.identifiers.tenant.TenantId;
import java.util.Objects;

public record TenantContextHolder(TenantId tenantId) {
  public TenantContextHolder {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
  }

  public static TenantContextHolder of(TenantId tenantId) {
    return new TenantContextHolder(tenantId);
  }
}
