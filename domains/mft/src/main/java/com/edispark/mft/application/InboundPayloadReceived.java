package com.edispark.mft.application;

import java.time.Instant;
import java.util.UUID;

/** Metadata-only notification that an inbound payload is durably available in MFT. */
public record InboundPayloadReceived(
        UUID eventId,
        String tenantId,
        UUID transferId,
        UUID partnerId,
        String messageId,
        String filename,
        String contentType,
        String sha256,
        long size,
        Instant receivedAt) {
}
