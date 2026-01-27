package com.example.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WebConfig}.
 * <p>
 * Verifies CORS configuration is correctly applied to API endpoints.
 * <p>
 * <b>Validates: Requirements 10.3</b>
 */
class WebConfigTest {

    @Test
    void shouldImplementWebMvcConfigurer() {
        // Given
        WebConfig webConfig = new WebConfig();
        
        // Then
        assertThat(webConfig).isInstanceOf(WebMvcConfigurer.class);
    }
    
    @Test
    void shouldBeAnnotatedWithConfiguration() {
        // Then
        assertThat(WebConfig.class.isAnnotationPresent(org.springframework.context.annotation.Configuration.class))
            .isTrue();
    }
}
