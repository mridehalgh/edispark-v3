package com.edispark.identifiers;

import com.github.ksuid.Ksuid;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public abstract class SortableId extends AbstractId<Ksuid> {

  public SortableId(Ksuid id) {
    super(id);
  }

  protected static Ksuid newSortableId() {
    return Ksuid.newKsuid();
  }

  @Override
  public String asString() {
    return this.raw().toString();
  }

  @Override
  public String toString() {
    return this.raw().toString();
  }
}
