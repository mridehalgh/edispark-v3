package com.edispark.integrationapplication.persistence;

import com.edispark.testing.ScopedRepositoryContract;
import com.edispark.partnerships.domain.model.*;
import java.time.Instant;
import java.util.*;

class DynamoPartnershipIsolationContractTest extends ScopedRepositoryContract<TradingPartner> {
    @Override protected Operations<TradingPartner> operations() {
        var fixture = new DynamoIsolationFixture();
        var repository = new DynamoPartnershipRepository(fixture.store(), fixture.json());
        return new Operations<>(repository::savePartner, repository::findPartner, repository::listPartners,
                Optional.of(repository::deletePartner));
    }

    @Override protected TradingPartner fixture(String owner, UUID id) {
        return new TradingPartner(id, owner, "Isolation fixture", List.of(), PartnerStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }
}
