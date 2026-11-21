package io.cottn.core.tenancy;

import com.edispark.identifiers.tenant.TenantId;
import java.util.Objects;
import java.util.function.Supplier;

/** Trusted ingress/worker boundary. Durable envelopes must still retain tenant ownership. */
public final class TenantExecution {
    private TenantExecution() { }

    public static void run(String tenantId, Runnable operation) {
        TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), operation);
    }

    public static <T> T get(String tenantId, Supplier<T> operation) {
        return TenantContext.getWhere(TenantContextHolder.of(TenantId.fromString(tenantId)), operation);
    }

    public static Runnable capture(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        try {
            var captured = TenantContext.getCurrentTenantContext();
            return () -> TenantContext.runWhere(captured, operation);
        } catch (NoTenantSetException absent) {
            // Global scheduled discovery runs without tenant authority and binds each verified item.
            return () -> TenantContext.runWithoutTenant(operation);
        }
    }

    public static <T> T io(String tenantId, IoOperation<T> operation) throws java.io.IOException {
        try (var ignored = TenantContext.where(TenantContextHolder.of(TenantId.fromString(tenantId)))) {
            return operation.get();
        }
    }

    @FunctionalInterface
    public interface IoOperation<T> {
        T get() throws java.io.IOException;
    }
}
