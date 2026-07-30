package com.edispark.rules;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class RestrictedCharacters implements CrnRule {

  // Match a single character present in the list below [A-Za-z0-9_/-.]
  private static final Pattern pattern = Pattern.compile("^([A-Za-z]|[0-9]|_|-|/|\\.)+$");
  private final String value;

  public RestrictedCharacters(String value) {
    this.value = value;
  }

  @Override
  public Boolean isValid() {
    if (StringUtils.isEmpty(value)) {
      return true;
    }

    return pattern.matcher(value).matches();
  }

  @Override
  public String getMessage() {
    return "Segment must contain only alphanumeric and the following characters - _ . /";
  }
}
