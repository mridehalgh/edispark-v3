package com.edispark.integrationapplication.events;

import com.edispark.partnerships.application.AgreementSnapshotPublisher;
import com.edispark.partnerships.domain.model.TradingAgreement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Component
@Profile("!local & !openapi-export")
final class EventBridgeAgreementSnapshotPublisher implements AgreementSnapshotPublisher {
    private final EventBridgeClient events;
    private final ObjectMapper json;
    private final String eventBus;
    EventBridgeAgreementSnapshotPublisher(@Qualifier("integrationEventBridge") EventBridgeClient events,
            ObjectMapper json,
            @Value("${integration.aws.event-bus}") String eventBus) {
        if (eventBus == null || eventBus.isBlank()) throw new IllegalStateException("INTEGRATION_EVENT_BUS is required");
        this.events = events; this.json = json; this.eventBus = eventBus;
    }
    @Override public void publish(TradingAgreement agreement) {
        try {
            var entry = PutEventsRequestEntry.builder().eventBusName(eventBus).source("com.edispark.partnerships")
                    .detailType("TradingAgreementActivated").detail(json.writeValueAsString(agreement)).build();
            var result = events.putEvents(PutEventsRequest.builder().entries(entry).build());
            if (result.failedEntryCount() != null && result.failedEntryCount() > 0) {
                throw new IllegalStateException("Agreement snapshot event was rejected");
            }
        } catch (IllegalStateException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Could not publish agreement snapshot", exception); }
    }
}
