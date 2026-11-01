package com.edispark.mft.infrastructure.as2;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.mft.application.MftDeliveryService;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.model.Transfer;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class MftDeliveryWorker {
    private final MftRepository repository;
    private final MftDeliveryService deliveryService;
    private final Clock clock;
    private final String leaseOwner;
    private final Duration leaseDuration;

    public MftDeliveryWorker(MftRepository repository, MftDeliveryService deliveryService, Clock clock) {
        this(repository, deliveryService, clock, UUID.randomUUID().toString(),
                Transfer.PROCESSING_RECOVERY_TIMEOUT);
    }

    MftDeliveryWorker(MftRepository repository, MftDeliveryService deliveryService, Clock clock,
                      String leaseOwner, Duration leaseDuration) {
        this.repository = repository;
        this.deliveryService = deliveryService;
        this.clock = clock;
        this.leaseOwner = leaseOwner;
        this.leaseDuration = leaseDuration;
    }

    @Scheduled(fixedDelayString = "${mft.delivery.poll-interval-ms:1000}")
    public void deliverDueTransfers() {
        repository.listPendingTransfers(clock.instant(), 10).forEach(transfer -> {
            if (!repository.tryAcquireDeliveryLease(transfer.tenantId(), transfer.id(), leaseOwner,
                    clock.instant(), clock.instant().plus(leaseDuration))) return;
            try {
                TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(transfer.tenantId())),
                        () -> deliveryService.deliver(transfer.id()));
            } catch (RuntimeException exception) {
                log.error("MFT delivery worker failed for transfer {}", transfer.id(), exception);
            } finally {
                repository.releaseDeliveryLease(transfer.tenantId(), transfer.id(), leaseOwner);
            }
        });
    }
}
