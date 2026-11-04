package com.edispark.transformations.domain;

/** Exact exchange coordinates supported by a mapping revision. */
public record MappingCompatibility(String direction, String documentType, String standard, String release,
        String messageType) { }
