package io.cottn.core.tenancy;

import com.edispark.identifiers.tenant.TenantId;
import java.util.ArrayList;
import java.util.List;

public abstract class TenantAwareAggregateRoot {

  private TenantId tenantId = TenantContext.getCurrentTenant();
  private final List<TenantAwareDomainEvent<?>> domainEvents = new ArrayList<>();

  protected void publish(TenantAwareDomainEvent<?> event) {
    domainEvents.add(event);
  }

  protected void setTenantId(TenantId tenantId) {
    this.tenantId = tenantId;
  }

  public TenantId tenantId() {
    return tenantId;
  }

  public List<TenantAwareDomainEvent<?>> domainEvents() {
    return List.copyOf(domainEvents);
  }

  public void clearDomainEvents() {
    domainEvents.clear();
  }
}
