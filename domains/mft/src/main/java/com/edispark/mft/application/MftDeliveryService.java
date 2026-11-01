package com.edispark.mft.application;

import com.edispark.mft.domain.model.PartnerProfile;
import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferId;
import com.edispark.mft.domain.model.TransferStatus;
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
        if (transfer.status() == TransferStatus.QUEUED || transfer.status() == TransferStatus.RETRY_SCHEDULED) {
            TransferStatus expectedStatus = transfer.status();
            transfer.startAttempt(startedAt);
            if (!repository.saveIfStatus(transfer, expectedStatus)) {
                returnIfConcurrentlyDelivered(tenantId, transferId);
                return;
            }
        } else if (transfer.status() != TransferStatus.PROCESSING
                && transfer.status() != TransferStatus.AWAITING_MDN) {
            throw new IllegalStateException("Transfer is not eligible for delivery");
        }
        try {
            byte[] payload = payloadStore.get(tenantId, transfer.payload()).readAllBytes();
            As2DeliveryResult result = exchange.send(partner, transfer, payload);
            transfer = repository.findTransfer(tenantId, transferId).orElse(transfer);
            if (transfer.status() == TransferStatus.DELIVERED) return;
            TransferStatus expectedStatus = transfer.status();
            if (result.awaitingAsyncMdn() && transfer.status() == TransferStatus.PROCESSING) {
                transfer.awaitMdn(clock.instant());
            } else if (result.awaitingAsyncMdn() && transfer.status() == TransferStatus.AWAITING_MDN) {
                return;
            } else if (result.processed()) {
                transfer.deliver(result.disposition(), clock.instant());
            } else {
                transfer.requireReview(result.disposition() == null ? "Partner returned no MDN" : result.disposition(), clock.instant());
            }
            if (!repository.saveIfStatus(transfer, expectedStatus)) {
                returnIfConcurrentlyDelivered(tenantId, transferId);
            }
            return;
        } catch (As2TransportException exception) {
            transfer = repository.findTransfer(tenantId, transferId).orElse(transfer);
            if (transfer.status() == TransferStatus.DELIVERED) return;
            handleFailure(transfer, exception.getMessage(), exception.retryable());
            return;
        } catch (IOException exception) {
            transfer = repository.findTransfer(tenantId, transferId).orElse(transfer);
            if (transfer.status() == TransferStatus.DELIVERED) return;
            handleFailure(transfer, "Transfer storage was unavailable", true);
            return;
        } catch (RuntimeException exception) {
            transfer = repository.findTransfer(tenantId, transferId).orElse(transfer);
            if (transfer.status() == TransferStatus.DELIVERED) return;
            handleFailure(transfer, "AS2 preparation or verification failed", false);
        }
    }

    private void handleFailure(Transfer transfer, String reason, boolean retryable) {
        TransferStatus expectedStatus = transfer.status();
        Instant now = clock.instant();
        if (retryable && transfer.attempts().size() < MAX_ATTEMPTS) {
            transfer.scheduleRetry(reason, now.plus(backoff(transfer.attempts().size())), now);
        } else {
            transfer.requireReview(reason, now);
        }
        if (repository.saveIfStatus(transfer, expectedStatus)) return;
        returnIfConcurrentlyDelivered(transfer.tenantId(), transfer.id());
    }

    private void returnIfConcurrentlyDelivered(String tenantId, TransferId transferId) {
        Transfer current = repository.findTransfer(tenantId, transferId)
                .orElseThrow(() -> new MftNotFoundException("Transfer was not found"));
        if (current.status() != TransferStatus.DELIVERED) {
            throw new IllegalStateException("Transfer changed during delivery");
        }
    }

    private Duration backoff(int attempt) {
        long cap = Math.min(300, 1L << Math.min(attempt + 1, 8));
        return Duration.ofSeconds(ThreadLocalRandom.current().nextLong(1, cap + 1));
    }
}
