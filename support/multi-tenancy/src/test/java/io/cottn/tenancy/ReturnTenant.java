package io.cottn.tenancy;

import io.cottn.core.tenancy.TenantContext;
import com.edispark.identifiers.tenant.TenantId;
import java.util.function.Supplier;
import org.slf4j.MDC;

public class ReturnTenant {

  public static final String TENANT_KEY = "tenant";

  public static Supplier<TenantId> currentTenantFromContext() {
    return TenantContext::getCurrentTenant;
  }

  public static Supplier<TenantId> currentTenantFromMdc() {
    return () -> TenantId.fromString(MDC.get(TENANT_KEY));
  }
}
