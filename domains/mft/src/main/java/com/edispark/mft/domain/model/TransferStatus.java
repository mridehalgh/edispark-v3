package com.edispark.mft.domain.model;

public enum TransferStatus {
    RECEIVED,
    QUEUED,
    PROCESSING,
    AWAITING_MDN,
    DELIVERED,
    RETRY_SCHEDULED,
    NEEDS_REVIEW,
    FAILED
}
