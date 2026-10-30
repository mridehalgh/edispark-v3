package com.edispark.identifiers.partition;

import com.edispark.identifiers.StringId;

public class Partition extends StringId {

  public Partition(String id) {
    super(id);
  }

  public static Partition fromString(String partionString) {
    return new Partition(partionString);
  }
}
