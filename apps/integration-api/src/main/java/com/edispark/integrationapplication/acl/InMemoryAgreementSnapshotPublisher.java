package com.edispark.integrationapplication.acl;

import com.edispark.partnerships.application.AgreementSnapshotPublisher;
import com.edispark.partnerships.domain.model.TradingAgreement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile({"local", "openapi-export"})
public final class InMemoryAgreementSnapshotPublisher implements AgreementSnapshotPublisher {
    private final Map<UUID, TradingAgreement> lastKnownGood = new ConcurrentHashMap<>();
    @Override public void publish(TradingAgreement agreement) { lastKnownGood.put(agreement.id(), agreement); }
    public TradingAgreement get(UUID id) { return lastKnownGood.get(id); }
}
