package io.cottn.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TenantContextTest {

  @Test
  void bindsTenantToTheCurrentOperationAndRestoresItAfterwards() {
    TenantId tenantId = TenantId.newTenantId();

    String resolved = TenantContext.getWhere(TenantContextHolder.of(tenantId),
        () -> TenantContext.getCurrentTenant().asString());

    assertThat(resolved).isEqualTo(tenantId.asString());
    assertThat(MDC.get("tenant")).isNull();
  }
}
