package com.edispark.transformations.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MappingRepository {
    MappingRevision save(MappingRevision mapping);
    Optional<MappingRevision> find(String tenantId, UUID id);
    List<MappingRevision> list(String tenantId);
}
