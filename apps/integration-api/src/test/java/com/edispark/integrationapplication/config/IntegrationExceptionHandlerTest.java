package com.edispark.integrationapplication.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IntegrationExceptionHandlerTest {
    private final IntegrationExceptionHandler handler = new IntegrationExceptionHandler();

    @Test
    void explainsHowToFixAnUnavailableDocumentExchange() {
        var request = request("correlation-123");

        var response = handler.invalid(new IllegalArgumentException(
                "Published mapping was not found for OUTBOUND ORDER_RESPONSE TRADACOMS 9"), request);

        assertThat(response.getBody()).satisfies(problem -> {
            assertThat(problem.code()).isEqualTo("COMPATIBLE_MAPPING_NOT_FOUND");
            assertThat(problem.stage()).isEqualTo("CONFIGURATION");
            assertThat(problem.retryable()).isFalse();
            assertThat(problem.correlationId()).isEqualTo("correlation-123");
            assertThat(problem.recommendedAction()).contains("publish").contains("mapping");
        });
    }

    @Test
    void marksUnavailableDependenciesAsRetryableWithoutExposingTheirResponseBody() {
        var response = handler.dependency(new org.springframework.web.client.HttpServerErrorException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "upstream details"), request(null));

        assertThat(response.getBody()).satisfies(problem -> {
            assertThat(problem.code()).isEqualTo("INTEGRATION_DEPENDENCY_UNAVAILABLE");
            assertThat(problem.retryable()).isTrue();
            assertThat(problem.recommendedAction()).contains("Try again");
            assertThat(problem.message()).doesNotContain("upstream details");
        });
    }

    private MockHttpServletRequest request(String correlationId) {
        var request = new MockHttpServletRequest();
        if (correlationId != null) request.addHeader("X-Correlation-ID", correlationId);
        return request;
    }
}
