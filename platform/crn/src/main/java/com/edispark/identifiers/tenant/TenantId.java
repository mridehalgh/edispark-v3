package com.edispark.identifiers.tenant;

import com.github.ksuid.Ksuid;
import com.edispark.identifiers.SortableId;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@EqualsAndHashCode(callSuper = true)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Getter
public class TenantId extends SortableId {

  public TenantId(Ksuid id) {
    super(id);
  }

  public static TenantId fromString(String tenantIdString) {
    return new TenantId(Ksuid.fromString(tenantIdString));
  }

  public static TenantId newTenantId() {
    return new TenantId(newSortableId());
  }
}
