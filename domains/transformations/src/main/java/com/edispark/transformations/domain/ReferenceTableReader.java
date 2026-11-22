package com.edispark.transformations.domain;

import java.util.UUID;

/** Resolves a published revision within the authenticated tenant. */
public interface ReferenceTableReader {
    ReferenceTableRevision require(UUID id);
}
