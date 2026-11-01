package com.edispark.transformations.domain;

public record MappingSample(String sourceFormat, String sourceDocument, String sourceRaw,
        String targetFormat, String targetDocument, String targetRaw) { }
