package com.edispark.identifiers;

public abstract class StringId extends AbstractId<String> {

  public StringId(String id) {
    super(id);
  }

  @Override
  public String asString() {
    return this.raw();
  }

  public String toString() {
    return this.raw();
  }
}
