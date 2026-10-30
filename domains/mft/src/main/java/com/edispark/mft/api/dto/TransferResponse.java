package com.edispark.mft.api.dto;

import com.edispark.mft.domain.model.Transfer;
import com.edispark.mft.domain.model.TransferAttempt;
import com.edispark.mft.domain.model.TransferDirection;
import com.edispark.mft.domain.model.TransferStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID partnerId,
        TransferDirection direction,
        TransferStatus status,
        String filename,
        String contentType,
        long size,
        String sha256,
        String as2MessageId,
        String mdnDisposition,
        String failureReason,
        UUID resendOf,
        Instant createdAt,
        Instant updatedAt,
        List<TransferAttempt> attempts) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.id().value(), transfer.partnerId(), transfer.direction(), transfer.status(),
                transfer.filename(), transfer.contentType(), transfer.payload() == null ? 0 : transfer.payload().size(),
                transfer.payload() == null ? null : transfer.payload().sha256(), transfer.as2MessageId(),
                transfer.mdnDisposition(), transfer.failureReason(),
                transfer.resendOf() == null ? null : transfer.resendOf().value(), transfer.createdAt(),
                transfer.updatedAt(), transfer.attempts());
    }
}
