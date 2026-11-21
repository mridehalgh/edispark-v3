package io.cottn.core.tenancy;

/** Infrastructure-only access to the authenticated tenant; business code uses scoped ports. */
public final class TenantAccess {
    private TenantAccess() { }

    public static String currentId() {
        return TenantContext.getCurrentTenant().asString();
    }

    public static String requireOwner(String owner) {
        String current = currentId();
        if (!current.equals(owner)) throw new SecurityException("Resource does not belong to the current tenant");
        return current;
    }
}
