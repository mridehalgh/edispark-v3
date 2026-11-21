package com.edispark.integrationapplication.persistence;

import com.edispark.testing.ScopedRepositoryContract;
import com.edispark.partnerships.domain.model.*;
import java.time.Instant;
import java.util.*;

class DynamoSsccIsolationContractTest extends ScopedRepositoryContract<SsccIssuer> {
    @Override protected Operations<SsccIssuer> operations() {
        var fixture = new DynamoIsolationFixture();
        var repository = new DynamoSsccRepository(fixture.store(), fixture.json());
        return new Operations<>(repository::save, repository::find, repository::list, Optional.empty());
    }

    @Override protected SsccIssuer fixture(String owner, UUID id) {
        return new SsccIssuer(id, owner, "Isolation fixture", "5012345", 0, 0, 999, 0, 12,
                false, true, SsccIssuerStatus.DRAFT, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
