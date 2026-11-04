package com.edispark.exchange.domain;

/** Value-free, machine-readable warning retained with exchange evidence. */
public record ExchangeWarning(String code, String sourcePath, String disposition, String message) { }
