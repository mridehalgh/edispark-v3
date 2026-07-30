package com.edispark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edispark.identifiers.region.Region;
import com.edispark.identifiers.resource.Resource;
import com.edispark.identifiers.service.Service;
import com.edispark.identifiers.tenant.TenantId;
import org.junit.jupiter.api.Test;

class CrnTest {

  private static final String TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";
  private static final String CRN =
      "crn:edispark:documents:eu-west-2:" + TENANT_ID + ":document-set/2OnHmVjaQa5eGWfT0HROcJkJmQ2";

  @Test
  void serializesATenantScopedDocumentResource() {
    Crn crn = new Crn(Service.fromString("documents"), Region.fromString("eu-west-2"),
        TenantId.fromString(TENANT_ID), Resource.fromString("document-set/2OnHmVjaQa5eGWfT0HROcJkJmQ2"));

    assertThat(crn.asString()).isEqualTo(CRN);
  }

  @Test
  void parsesAGlobalResource() {
    Crn crn = Crn.fromString("crn:edispark:config::" + TENANT_ID + ":retention-policy/default");

    assertThat(crn.asString()).isEqualTo(
        "crn:edispark:config::" + TENANT_ID + ":retention-policy/default");
  }

  @Test
  void rejectsNonEdiSparkPartitions() {
    assertThatThrownBy(() -> Crn.fromString(CRN.replace("crn:edispark", "crn:other")))
        .isInstanceOf(MalformedCrnException.class);
  }

  @Test
  void rejectsMissingResourceIdentifiers() {
    assertThatThrownBy(() -> Crn.fromString("crn:edispark:documents:eu-west-2:" + TENANT_ID + ":"))
        .isInstanceOf(MalformedCrnException.class);
  }
}
