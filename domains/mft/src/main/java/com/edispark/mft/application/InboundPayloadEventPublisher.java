package com.edispark.mft.application;

@FunctionalInterface
public interface InboundPayloadEventPublisher {
    void publish(InboundPayloadReceived event);

    static InboundPayloadEventPublisher noOp() {
        return event -> { };
    }
}
