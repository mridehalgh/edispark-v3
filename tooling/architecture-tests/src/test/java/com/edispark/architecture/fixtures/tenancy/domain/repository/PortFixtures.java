package com.edispark.architecture.fixtures.tenancy.domain.repository;

import com.edispark.identifiers.tenant.TenantId;
import java.util.UUID;

public final class PortFixtures {
    public interface TypedRepository { void find(TenantId selector, UUID id); }
    public interface StringRepository { void find(String selector, UUID id); }
    public interface UnclassifiedRepository { void find(String reference); }
    public interface BusinessKeyRepository { void find(String businessKey); }
    public interface GlobalRepository { void findInboundPartner(String local, String remote); }
    public interface ScopedRepository { void find(UUID id); }

    public record OwnedEvent(TenantId tenantId, UUID entityId) { }
}
