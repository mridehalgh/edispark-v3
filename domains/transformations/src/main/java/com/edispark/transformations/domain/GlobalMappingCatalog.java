package com.edispark.transformations.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GlobalMappingCatalog {
    List<MappingRevision> list();
    Optional<MappingRevision> find(UUID id);
}
