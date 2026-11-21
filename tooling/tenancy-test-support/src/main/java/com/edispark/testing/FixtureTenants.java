package com.edispark.testing;

import io.cottn.core.tenancy.TenantExecution;
import java.util.function.Function;

/** Fixture construction is trusted infrastructure, unlike the business operation under test. */
public final class FixtureTenants {
    private FixtureTenants() { }

    public static <T, R> R persist(T record, Function<T, String> owner, Function<T, R> persist) {
        return TenantExecution.get(owner.apply(record), () -> persist.apply(record));
    }
}
