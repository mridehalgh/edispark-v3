package io.cottn.tenancy;

import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.*;

class TenantIsolationTest {
    @Test
    void absentContextAndForeignOwnershipFailClosed() {
        assertThatThrownBy(TenantAccess::currentId).isInstanceOf(NoTenantSetException.class);
        var a = TenantId.newTenantId();
        TenantContext.runWhere(TenantContextHolder.of(a), () -> {
            assertThat(TenantAccess.requireOwner(a.asString())).isEqualTo(a.asString());
            assertThatThrownBy(() -> TenantAccess.requireOwner(TenantId.newTenantId().asString()))
                    .isInstanceOf(SecurityException.class);
        });
        assertThatThrownBy(TenantAccess::currentId).isInstanceOf(NoTenantSetException.class);
    }

    @Test
    void nestedFailuresRestoreOuterContextAndMdc() {
        var a = TenantId.newTenantId();
        var b = TenantId.newTenantId();
        TenantContext.runWhere(TenantContextHolder.of(a), () -> {
            assertThatThrownBy(() -> TenantContext.runWhere(TenantContextHolder.of(b), () -> {
                throw new IllegalStateException("fixture failure");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(TenantContext.getCurrentTenant()).isEqualTo(a);
            assertThat(MDC.get("tenant")).isEqualTo(a.asString());
        });
        assertThat(MDC.get("tenant")).isNull();
    }

    @Test
    void aReusedThreadCannotBorrowPreviousTenants() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            for (int index = 0; index < 10; index++) {
                var tenant = TenantId.newTenantId();
                assertThat(executor.submit(() -> TenantContext.getWhere(TenantContextHolder.of(tenant), TenantAccess::currentId)).get())
                        .isEqualTo(tenant.asString());
                assertThat(executor.submit(() -> {
                    assertThatThrownBy(TenantAccess::currentId).isInstanceOf(NoTenantSetException.class);
                    return MDC.get("tenant");
                }).get()).isNull();
            }
        }
    }

    @Test
    void capturedTasksPropagateAndRestoreContextWithoutInheritableThreadLocals() throws Exception {
        var tenant = TenantId.newTenantId();
        var task = TenantContext.getWhere(TenantContextHolder.of(tenant), () -> TenantExecution.capture(() ->
                assertThat(TenantAccess.currentId()).isEqualTo(tenant.asString())));
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(task).get();
            executor.submit(() -> assertThatThrownBy(TenantAccess::currentId)
                    .isInstanceOf(NoTenantSetException.class)).get();
        }
        Runnable unscoped = TenantExecution.capture(() -> assertThatThrownBy(TenantAccess::currentId)
                .isInstanceOf(NoTenantSetException.class));
        TenantExecution.run(tenant.asString(), () -> {
            unscoped.run();
            assertThat(TenantAccess.currentId()).isEqualTo(tenant.asString());
            assertThat(MDC.get("tenant")).isEqualTo(tenant.asString());
        });
    }

    @net.jqwik.api.Property(tries = 100)
    void arbitraryFailuresCannotLeakTenantState(@net.jqwik.api.ForAll int sequence) {
        var owner = TenantId.newTenantId();
        var nested = TenantId.newTenantId();
        TenantContext.runWhere(TenantContextHolder.of(owner), () -> {
            if ((sequence & 1) == 0) {
                assertThatThrownBy(() -> TenantExecution.run(nested.asString(), () -> {
                    throw new IllegalStateException("fixture failure");
                })).isInstanceOf(IllegalStateException.class);
            } else {
                TenantExecution.run(nested.asString(), () -> assertThat(TenantAccess.currentId()).isEqualTo(nested.asString()));
            }
            assertThat(TenantAccess.currentId()).isEqualTo(owner.asString());
        });
        assertThatThrownBy(TenantAccess::currentId).isInstanceOf(NoTenantSetException.class);
    }
}
