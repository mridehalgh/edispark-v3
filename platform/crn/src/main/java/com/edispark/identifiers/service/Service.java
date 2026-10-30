package com.edispark.identifiers.service;

import com.edispark.identifiers.StringId;

public class Service extends StringId {

  public Service(String id) {
    super(id);
  }

  public static Service fromString(String serviceString) {
    return new Service(serviceString);
  }
}
