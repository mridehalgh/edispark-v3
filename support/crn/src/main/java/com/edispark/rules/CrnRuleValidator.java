package com.edispark.rules;

public class CrnRuleValidator {

  protected void checkRule(CrnRule rule) throws CrnRuleValidationException {
    if (!rule.isValid()) {
      throw new CrnRuleValidationException(rule);
    }
  }
}
