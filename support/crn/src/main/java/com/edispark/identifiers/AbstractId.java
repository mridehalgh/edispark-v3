package com.edispark.identifiers;

import com.edispark.rules.CrnRuleValidator;
import com.edispark.rules.RuleRegistry;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
public abstract class AbstractId<T> extends CrnRuleValidator implements Identity<T> {

  private static final long serialVersionUID = 1L;
  private final T id;

  public AbstractId(T id) {
    this.id = id;
    checkRule(RuleRegistry.noRestrictedCharacters(this.asString()));
  }

  public T raw() {
    return this.id;
  }
}
