package com.edispark.transformations.infrastructure;

import com.edispark.testing.ScopedRepositoryContract;
import com.edispark.transformations.domain.*;
import java.time.Instant;
import java.util.*;

class MappingIsolationContractTest extends ScopedRepositoryContract<MappingRevision> {
    @Override protected Operations<MappingRevision> operations() {
        var repository = new InMemoryMappingRepository();
        return new Operations<>(repository::save, repository::find, repository::list, Optional.of(repository::delete));
    }

    @Override protected MappingRevision fixture(String owner, UUID id) {
        return new MappingRevision(id, owner, "Isolation fixture", 1, id, id, MappingStatus.DRAFT,
                List.of(), null, Instant.EPOCH, null);
    }
}
