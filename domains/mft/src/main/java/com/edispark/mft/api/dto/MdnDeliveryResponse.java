package com.edispark.mft.api.dto;

import com.edispark.mft.domain.model.MdnDelivery;
import com.edispark.mft.domain.model.MdnDeliveryStatus;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record MdnDeliveryResponse(UUID id, UUID partnerId, URI callback, String originalMessageId,
                                  MdnDeliveryStatus status, int attempts, Instant nextAttemptAt,
                                  String lastFailure, Instant createdAt, Instant updatedAt) {
    public static MdnDeliveryResponse from(MdnDelivery delivery) {
        return new MdnDeliveryResponse(delivery.id(), delivery.partnerId(), delivery.callback(),
                delivery.originalMessageId(), delivery.status(), delivery.attempts(), delivery.nextAttemptAt(),
                delivery.lastFailure(), delivery.createdAt(), delivery.updatedAt());
    }
}
