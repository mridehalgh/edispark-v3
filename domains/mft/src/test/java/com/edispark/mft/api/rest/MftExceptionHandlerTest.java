package com.edispark.mft.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MftExceptionHandlerTest {
    private final MftExceptionHandler handler = new MftExceptionHandler();

    @Test
    void explainsThatAStorageFailureCanBeRetried() {
        var response = handler.storageProblem(new IOException("sensitive storage detail"), "transfer-42");

        assertThat(response.getBody()).satisfies(problem -> {
            assertThat(problem.code()).isEqualTo("MFT_STORAGE_UNAVAILABLE");
            assertThat(problem.stage()).isEqualTo("PERSISTENCE");
            assertThat(problem.retryable()).isTrue();
            assertThat(problem.recommendedAction()).contains("Try again");
            assertThat(problem.correlationId()).isEqualTo("transfer-42");
            assertThat(problem.message()).doesNotContain("sensitive storage detail");
        });
    }
}
