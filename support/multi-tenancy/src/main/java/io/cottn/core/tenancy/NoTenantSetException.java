package io.cottn.core.tenancy;

public class NoTenantSetException extends RuntimeException {
  public NoTenantSetException() {
    super("No tenant is bound to the current request");
  }
}
