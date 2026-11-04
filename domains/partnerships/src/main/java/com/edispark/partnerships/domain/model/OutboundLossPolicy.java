package com.edispark.partnerships.domain.model;

/** Controls whether an outbound exchange may transport a document with documented source-only values. */
public enum OutboundLossPolicy {
    STRICT,
    ALLOW_WITH_WARNING
}
