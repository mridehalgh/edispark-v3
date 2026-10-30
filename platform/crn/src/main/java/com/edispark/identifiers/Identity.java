package com.edispark.identifiers;

public interface Identity<T> {

  T raw();

  String asString();
}
