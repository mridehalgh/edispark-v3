package io.cottn.core.tenancy;

import com.edispark.identifiers.tenant.TenantId;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.MDC;

/** Binds an authenticated tenant to the current request execution. */
public final class TenantContext {

  private static final ThreadLocal<TenantContextHolder> CURRENT_TENANT = new ThreadLocal<>();

  private TenantContext() {
  }

  public static TenantId getCurrentTenant() {
    return getCurrentTenantContext().tenantId();
  }

  public static TenantContextHolder getCurrentTenantContext() {
    TenantContextHolder context = CURRENT_TENANT.get();
    if (context == null) {
      throw new NoTenantSetException();
    }
    return context;
  }

  public static Scope where(TenantContextHolder tenant) {
    TenantContextHolder previous = CURRENT_TENANT.get();
    String previousMdcTenant = MDC.get("tenant");
    set(tenant);
    return () -> restore(previous, previousMdcTenant);
  }

  public static void runWhere(TenantContextHolder tenant, Runnable runnable) {
    Objects.requireNonNull(runnable, "runnable must not be null");
    try (Scope ignored = where(tenant)) {
      runnable.run();
    }
  }

  public static <R> R getWhere(TenantContextHolder tenant, Supplier<? extends R> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    try (Scope ignored = where(tenant)) {
      return supplier.get();
    }
  }

  private static void set(TenantContextHolder tenant) {
    TenantContextHolder requiredTenant = Objects.requireNonNull(tenant, "tenant context must not be null");
    CURRENT_TENANT.set(requiredTenant);
    MDC.put("tenant", requiredTenant.tenantId().asString());
  }

  private static void restore(TenantContextHolder previous, String previousMdcTenant) {
    if (previous == null) {
      CURRENT_TENANT.remove();
    } else {
      CURRENT_TENANT.set(previous);
    }
    if (previousMdcTenant == null) {
      MDC.remove("tenant");
    } else {
      MDC.put("tenant", previousMdcTenant);
    }
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
