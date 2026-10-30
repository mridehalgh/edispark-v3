package com.edispark.mft.application;

import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.repository.MftRepository;
import com.edispark.mft.domain.repository.PayloadStore;
import io.cottn.core.tenancy.TenantContext;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public final class MftDeliveryService {
    private static final int MAX_ATTEMPTS = 5;
    private final MftRepository repository;
    private final PayloadStore payloadStore;
    private final As2ExchangePort exchange;
    private final Clock clock;

    public MftDeliveryService(MftRepository repository, PayloadStore payloadStore,
                              As2ExchangePort exchange, Clock clock) {
        this.repository = repository;
        this.payloadStore = payloadStore;
        this.exchange = exchange;
        this.clock = clock;
    }

    public void deliver(TransferId transferId) {
        String tenantId = TenantContext.getCurrentTenant().asString();
        Transfer transfer = repository.findTransfer(tenantId, transferId)
                .orElseThrow(() -> new MftNotFoundException("Transfer was not found"));
        PartnerProfile partner = repository.findPartner(tenantId, transfer.partnerId())
                .orElseThrow(() -> new MftNotFoundException("Partner was not found"));
        Instant startedAt = clock.instant();
        transfer.startAttempt(startedAt);
        repository.save(transfer);
        try {
            byte[] payload = payloadStore.get(tenantId, transfer.payload()).readAllBytes();
            As2DeliveryResult result = exchange.send(partner, transfer, payload);
            if (result.awaitingAsyncMdn() && transfer.status() == com.edispark.mft.domain.model.TransferStatus.PROCESSING) {
                transfer.awaitMdn(clock.instant());
            } else if (result.awaitingAsyncMdn()) {
                return;
            }
            else if (result.processed()) {
                transfer.deliver(result.disposition(), clock.instant());
            } else {
                transfer.requireReview(result.disposition() == null ? "Partner returned no MDN" : result.disposition(), clock.instant());
            }
        } catch (As2TransportException exception) {
            handleFailure(transfer, exception.getMessage(), exception.retryable());
        } catch (IOException exception) {
            handleFailure(transfer, "Transfer storage was unavailable", true);
        } catch (RuntimeException exception) {
            handleFailure(transfer, "AS2 preparation or verification failed", false);
        }
        repository.save(transfer);
    }

    private void handleFailure(Transfer transfer, String reason, boolean retryable) {
        Instant now = clock.instant();
        if (retryable && transfer.attempts().size() < MAX_ATTEMPTS) {
            transfer.scheduleRetry(reason, now.plus(backoff(transfer.attempts().size())), now);
        } else {
            transfer.requireReview(reason, now);
        }
    }

    private Duration backoff(int attempt) {
        long cap = Math.min(300, 1L << Math.min(attempt + 1, 8));
        return Duration.ofSeconds(ThreadLocalRandom.current().nextLong(1, cap + 1));
    }
}
