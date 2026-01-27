package com.example.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global web configuration for the application.
 * <p>
 * Configures cross-cutting web concerns including CORS (Cross-Origin Resource Sharing)
 * to allow API access from different origins.
 * <p>
 * CORS Configuration:
 * <ul>
 *   <li>Applies to all /api/** endpoints</li>
 *   <li>Allows all origins (*)</li>
 *   <li>Allows standard HTTP methods (GET, POST, PUT, DELETE, OPTIONS)</li>
 *   <li>Allows all headers</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html">Spring MVC CORS Support</a>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
