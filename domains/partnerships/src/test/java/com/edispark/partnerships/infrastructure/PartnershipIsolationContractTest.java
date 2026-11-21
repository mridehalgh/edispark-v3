package com.edispark.partnerships.infrastructure;

import com.edispark.testing.ScopedRepositoryContract;
import com.edispark.partnerships.domain.model.*;
import java.time.Instant;
import java.util.*;

class PartnershipIsolationContractTest extends ScopedRepositoryContract<TradingPartner> {
    @Override protected Operations<TradingPartner> operations() {
        var repository = new InMemoryPartnershipRepository();
        return new Operations<>(repository::savePartner, repository::findPartner, repository::listPartners,
                Optional.of(repository::deletePartner));
    }

    @Override protected TradingPartner fixture(String owner, UUID id) {
        return new TradingPartner(id, owner, "Isolation fixture", List.of(), PartnerStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }
}
