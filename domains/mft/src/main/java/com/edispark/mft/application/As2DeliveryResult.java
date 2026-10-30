package com.edispark.mft.application;

public record As2DeliveryResult(boolean awaitingAsyncMdn, String disposition, String mdnMessageId) {
}
