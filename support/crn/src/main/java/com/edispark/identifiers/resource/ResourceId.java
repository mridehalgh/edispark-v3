package com.edispark.identifiers.resource;

import com.edispark.identifiers.StringId;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class ResourceId extends StringId {

  public ResourceId(String id) {
    super(id);
  }

  public static ResourceId fromString(String resourceId) {
    return new ResourceId(resourceId);
  }

  @Override
  public String asString() {
    return this.raw();
  }

  @Override
  public String toString() {
    return super.raw();
  }
}
