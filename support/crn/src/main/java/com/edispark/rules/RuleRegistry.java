package com.edispark.rules;

import java.util.regex.Pattern;

public class RuleRegistry {

  private static final Pattern pattern = Pattern.compile("^([A-Za-z]|[0-9]|_|-|/|\\.)+$");

  public static RestrictedCharacters noRestrictedCharacters(String value) {
    return new RestrictedCharacters(value);
  }

  public static boolean containsRestrictedCharacters2(String value) {
    return !pattern.matcher(value).matches();
  }
}
