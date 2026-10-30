package com.edispark.mft.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MdnDispositionBehaviorTest {
    @Test
    void acceptsOnlyAConclusiveProcessedDisposition() {
        var result = new As2DeliveryResult(false,
                "automatic-action/MDN-sent-automatically; processed", "mdn-1");

        assertThat(result.processed()).isTrue();
    }

    @Test
    void rejectsProcessedDispositionsThatContainAnErrorModifier() {
        var result = new As2DeliveryResult(false,
                "automatic-action/MDN-sent-automatically; processed/Error: authentication-failed", "mdn-2");

        assertThat(result.processed()).isFalse();
    }

    @Test
    void rejectsMissingDispositionEvidence() {
        assertThat(new As2DeliveryResult(false, null, null).processed()).isFalse();
    }
}
