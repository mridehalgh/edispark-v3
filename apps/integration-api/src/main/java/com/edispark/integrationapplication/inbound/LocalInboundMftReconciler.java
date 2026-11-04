package com.edispark.integrationapplication.inbound;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Local polling adapter that provides the same at-least-once semantics as production SQS. */
@Component
@Profile("local & !openapi-export")
@Slf4j
public final class LocalInboundMftReconciler {
    private final InboundModuleAccess modules;
    private final Consumer<InboundMftPayloadEvent> processor;
    private final String tenantId;
    private final Set<UUID> completed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> quarantined = ConcurrentHashMap.newKeySet();

    @Autowired
    public LocalInboundMftReconciler(InboundModuleAccess modules, InboundMftEventProcessor processor,
            @Value("${integration.local-demo.tenant-id}") String tenantId) {
        this(modules, processor::process, tenantId);
    }

    LocalInboundMftReconciler(InboundModuleAccess modules, Consumer<InboundMftPayloadEvent> processor,
            String tenantId) {
        this.modules = modules;
        this.processor = processor;
        this.tenantId = tenantId;
    }

    @Scheduled(initialDelayString = "${integration.inbound.local-initial-delay-ms:2000}",
            fixedDelayString = "${integration.inbound.local-poll-delay-ms:5000}")
    public void reconcile() {
        modules.inboundTransfers(tenantId).stream()
                .filter(this::pending).forEach(this::process);
    }

    private boolean pending(InboundMftPayloadEvent event) {
        return !completed.contains(event.eventId()) && !quarantined.contains(event.eventId());
    }

    private void process(InboundMftPayloadEvent event) {
        try {
            processor.accept(event);
            completed.add(event.eventId());
        } catch (IllegalArgumentException exception) {
            quarantined.add(event.eventId());
            log.warn("Local inbound MFT event quarantined tenantId={} eventId={} transferId={} reason={} "
                            + "outcome=needs_review",
                    event.tenantId(), event.eventId(), event.transferId(), safeReason(exception));
        } catch (RuntimeException exception) {
            log.error("Local inbound MFT event failed tenantId={} eventId={} transferId={} failureType={} "
                            + "outcome=retry_scheduled",
                    event.tenantId(), event.eventId(), event.transferId(), exception.getClass().getSimpleName());
        }
    }

    private String safeReason(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Invalid inbound export";
        String singleLine = message.replaceAll("[\\r\\n\\t]", " ");
        return singleLine.length() <= 240 ? singleLine : singleLine.substring(0, 240);
    }
}
