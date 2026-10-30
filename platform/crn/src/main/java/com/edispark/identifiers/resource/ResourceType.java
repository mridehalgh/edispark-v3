package com.edispark.identifiers.resource;

import com.edispark.identifiers.StringId;

public class ResourceType extends StringId {

  public ResourceType(String id) {
    super(id);
  }

  public static ResourceType fromString(String resType) {
    return new ResourceType(resType);
  }
}
