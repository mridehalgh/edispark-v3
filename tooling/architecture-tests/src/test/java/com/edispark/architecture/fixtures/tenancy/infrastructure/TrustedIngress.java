package com.edispark.architecture.fixtures.tenancy.infrastructure;

import io.cottn.core.tenancy.TenantContext;

public final class TrustedIngress {
    public void execute() { TenantContext.runWhere(null, () -> { }); }

    public static class Persistence { }
}
