package com.edispark.identifiers.region;

import com.edispark.identifiers.StringId;

public class Region extends StringId {

  public Region(String id) {
    super(id);
  }

  public static Region fromString(String regionString) {
    return new Region(regionString);
  }
}
