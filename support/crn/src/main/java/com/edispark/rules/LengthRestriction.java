package com.edispark.rules;

import com.edispark.Crn;

public class LengthRestriction implements CrnRule {

  private static final int MAX_LENGTH = 256;
  private final Crn value;

  public LengthRestriction(Crn value) {
    this.value = value;
  }

  @Override
  public Boolean isValid() {
    return value.toString().length() <= MAX_LENGTH;
  }

  @Override
  public String getMessage() {
    return "A CRN must be less than or equal to 256 characters";
  }
}
