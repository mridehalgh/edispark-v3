package io.cottn.tenancy


import io.cottn.core.tenancy.TenantContext
import io.cottn.core.tenancy.TenantContextHolder
import io.cttn.identifiers.tenant.TenantId
import spock.lang.Specification

class TenantContextSpec extends Specification {

    def "sets tenant context"() {
        given: "a tenant id"
        def id = TenantId.newAccountId()

        when: "setting tenant context"
        def tenantContextHolder = TenantContextHolder.of(id);
        def tenantId = TenantContext.getWhere(tenantContextHolder, supplier)

        then: "tenant context is set"
        tenantId.asString() == id.asString()

        where:
        supplier << [ReturnTenant.currentTenantFromContext(), ReturnTenant.currentTenantFromMdc()]
    }
}
