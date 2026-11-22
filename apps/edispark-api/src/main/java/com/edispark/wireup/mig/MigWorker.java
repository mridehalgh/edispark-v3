package com.edispark.wireup.mig;

import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

final class MigWorker implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MigWorker.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore slots = new Semaphore(4);
    private final Set<String> tenants = ConcurrentHashMap.newKeySet();
    private final MigStore store;
    private final MigWorkflow workflow;
    private final MeterRegistry meters;

    MigWorker(MigStore store, MigWorkflow workflow, MeterRegistry meters) {
        this.store = store;
        this.workflow = workflow;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${edispark.mig.poll-ms:3000}", initialDelayString = "#{T(java.util.concurrent.ThreadLocalRandom).current().nextInt(1000, 3001)}")
    void poll() {
        if (slots.availablePermits() == 0) return;
        for (var event : store.work()) {
            if (!slots.tryAcquire()) return;
            if (!tenants.add(event.tenant())) {
                slots.release();
                continue;
            }
            executor.submit(() -> run(event));
        }
    }

    private void run(MigStore.Work event) {
        try {
            TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(event.tenant())), () -> workflow.process(event));
        } catch (RuntimeException failed) {
            meters.counter("edispark.mig.worker.failures").increment();
            LOG.warn("MIG worker failed job={} failureType={}", event.id(), failed.getClass().getSimpleName());
        } finally {
            tenants.remove(event.tenant());
            slots.release();
        }
    }

    @Override public void close() { executor.shutdownNow(); }
}
