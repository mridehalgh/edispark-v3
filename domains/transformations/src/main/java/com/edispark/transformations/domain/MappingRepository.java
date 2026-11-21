package com.edispark.transformations.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MappingRepository extends MappingCreation {
    MappingRevision save(MappingRevision mapping);
    Optional<MappingRevision> find(UUID id);
    List<MappingRevision> list();
    void delete(UUID id);
}
