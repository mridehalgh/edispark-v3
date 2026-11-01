package com.edispark.integrationapplication.inbound;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Local polling adapter that provides the same at-least-once semantics as production SQS. */
@Component
@Profile("local & !openapi-export")
public final class LocalInboundMftReconciler {
    private final RestClient mft;
    private final InboundMftEventProcessor processor;
    private final String token;
    private final String tenantId;
    private final Set<UUID> completed = ConcurrentHashMap.newKeySet();

    public LocalInboundMftReconciler(@Qualifier("mftRestClient") RestClient mft,
            InboundMftEventProcessor processor,
            @Value("${edispark.internal.token}") String token,
            @Value("${integration.local-demo.tenant-id}") String tenantId) {
        this.mft = mft;
        this.processor = processor;
        this.token = token;
        this.tenantId = tenantId;
    }

    @Scheduled(initialDelayString = "${integration.inbound.local-initial-delay-ms:2000}",
            fixedDelayString = "${integration.inbound.local-poll-delay-ms:5000}")
    public void reconcile() {
        InboundMftPayloadEvent[] events = mft.get().uri("/internal/mft/transfers")
                .header("X-EdiSpark-Internal-Token", token).header("X-EdiSpark-Tenant-Id", tenantId)
                .retrieve().body(InboundMftPayloadEvent[].class);
        if (events == null) return;
        Arrays.stream(events).filter(event -> !completed.contains(event.eventId())).forEach(event -> {
            processor.process(event);
            completed.add(event.eventId());
        });
    }
}
