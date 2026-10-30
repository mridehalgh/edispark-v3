package com.edispark.mft.infrastructure.as2;

import com.edispark.identifiers.tenant.TenantId;
import com.edispark.mft.application.MftDeliveryService;
import com.edispark.mft.domain.repository.MftRepository;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class MftDeliveryWorker {
    private final MftRepository repository;
    private final MftDeliveryService deliveryService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${mft.delivery.poll-interval-ms:1000}")
    public void deliverDueTransfers() {
        repository.listPendingTransfers(clock.instant(), 10).forEach(transfer -> {
            try {
                TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(transfer.tenantId())),
                        () -> deliveryService.deliver(transfer.id()));
            } catch (RuntimeException exception) {
                log.error("MFT delivery worker failed for transfer {}", transfer.id(), exception);
            }
        });
    }
}
