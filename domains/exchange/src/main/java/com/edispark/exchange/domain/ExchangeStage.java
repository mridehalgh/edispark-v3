package com.edispark.exchange.domain;

import java.time.Instant;

public record ExchangeStage(ExchangeStatus status, Instant occurredAt, String detail) { }
