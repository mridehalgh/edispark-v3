package com.edispark.integrationapplication.inbound;

import java.time.Instant;
import java.util.UUID;

public record InboundMftPayloadEvent(UUID eventId, String tenantId, UUID transferId, UUID partnerId,
        String messageId, String filename, String contentType, String sha256, long size, Instant receivedAt) { }
