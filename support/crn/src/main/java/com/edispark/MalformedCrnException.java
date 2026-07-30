package com.edispark;

public class MalformedCrnException extends RuntimeException {

  private static final String MALFORMED_ERROR = "CRN must follow the format: " + Crn.FORMAT;

  public MalformedCrnException() {
    super(MALFORMED_ERROR);
  }

  public MalformedCrnException(Throwable cause) {
    super(MALFORMED_ERROR, cause);
  }
}
