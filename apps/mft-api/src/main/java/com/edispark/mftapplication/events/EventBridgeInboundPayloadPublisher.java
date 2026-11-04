package com.edispark.mftapplication.events;

import com.edispark.mft.application.InboundPayloadEventPublisher;
import com.edispark.mft.application.InboundPayloadReceived;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

@Configuration
@Profile("production")
public class EventBridgeInboundPayloadPublisher {
    @Bean
    EventBridgeClient mftEventBridge(@Value("${mft.aws.region}") String region) {
        return EventBridgeClient.builder().region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build()).build();
    }

    @Bean
    InboundPayloadEventPublisher inboundPayloadEventPublisher(
            @Qualifier("mftEventBridge") EventBridgeClient client, ObjectMapper mapper,
            @Value("${mft.events.bus-name}") String eventBus) {
        return event -> {
            try {
                var response = client.putEvents(PutEventsRequest.builder().entries(PutEventsRequestEntry.builder()
                        .eventBusName(eventBus).source("com.edispark.mft")
                        .detailType("MftInboundPayloadReceived").detail(mapper.writeValueAsString(event)).build()).build());
                if (response.failedEntryCount() != null && response.failedEntryCount() > 0) {
                    throw new IllegalStateException("Inbound MFT event publication was rejected");
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Inbound MFT event could not be serialized", exception);
            }
        };
    }
}
