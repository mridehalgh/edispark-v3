package com.edispark.rules;

import lombok.Getter;

@Getter
public class CrnRuleValidationException extends RuntimeException {

  private final CrnRule brokenRule;

  public CrnRuleValidationException(CrnRule brokenRule) {
    super(brokenRule.getMessage());
    this.brokenRule = brokenRule;
  }
}
