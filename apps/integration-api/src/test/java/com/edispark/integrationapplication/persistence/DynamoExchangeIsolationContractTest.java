package com.edispark.integrationapplication.persistence;

import com.edispark.testing.ScopedRepositoryContract;
import com.edispark.exchange.domain.*;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import java.time.Instant;
import java.util.*;

class DynamoExchangeIsolationContractTest extends ScopedRepositoryContract<EdiExchange> {
    @Override protected Operations<EdiExchange> operations() {
        var fixture = new DynamoIsolationFixture();
        var repository = new DynamoExchangeRepository(fixture.store(), fixture.json());
        return new Operations<>(repository::save, repository::find, repository::list, Optional.empty());
    }

    @Override protected EdiExchange fixture(String owner, UUID id) {
        return EdiExchange.restore(id, owner, ExchangeDirection.OUTBOUND, id, id, 1, id, "same-key",
                new DocumentReference(id, id, id, "sha256:fixture"), null, null, ExchangeStatus.RECEIVED,
                Instant.EPOCH, Instant.EPOCH, List.of(new ExchangeStage(ExchangeStatus.RECEIVED, Instant.EPOCH, "Isolation fixture")), 1, "1");
    }
}
