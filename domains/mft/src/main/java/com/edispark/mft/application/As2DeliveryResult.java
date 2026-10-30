package com.edispark.mft.application;

import java.util.Locale;

public record As2DeliveryResult(boolean awaitingAsyncMdn, String disposition, String mdnMessageId) {
    public boolean processed() {
        return disposition != null
                && disposition.toLowerCase(Locale.ROOT).matches(".*;\\s*processed\\s*$");
    }
}
