package com.edispark.integrationapplication.inbound;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Local polling adapter that provides the same at-least-once semantics as production SQS. */
@Component
@Profile("local & !openapi-export")
public final class LocalInboundMftReconciler {
    private final InboundModuleAccess modules;
    private final InboundMftEventProcessor processor;
    private final String tenantId;
    private final Set<UUID> completed = ConcurrentHashMap.newKeySet();

    public LocalInboundMftReconciler(InboundModuleAccess modules, InboundMftEventProcessor processor,
            @Value("${integration.local-demo.tenant-id}") String tenantId) {
        this.modules = modules;
        this.processor = processor;
        this.tenantId = tenantId;
    }

    @Scheduled(initialDelayString = "${integration.inbound.local-initial-delay-ms:2000}",
            fixedDelayString = "${integration.inbound.local-poll-delay-ms:5000}")
    public void reconcile() {
        modules.inboundTransfers(tenantId).stream()
                .filter(event -> !completed.contains(event.eventId())).forEach(event -> {
            processor.process(event);
            completed.add(event.eventId());
        });
    }
}
