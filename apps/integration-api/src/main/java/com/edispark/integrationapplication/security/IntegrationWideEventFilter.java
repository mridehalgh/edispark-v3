package com.edispark.integrationapplication.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

final class IntegrationWideEventFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger("edispark.integration.request");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        String correlationId = correlationId(request.getHeader("X-Correlation-ID"));
        response.setHeader("X-Correlation-ID", correlationId);
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            var event = failure == null && response.getStatus() < 500 ? LOG.atInfo() : LOG.atError();
            event.addKeyValue("event", "integration_request_completed")
                    .addKeyValue("service", "integration-api")
                    .addKeyValue("version", version())
                    .addKeyValue("region", environment("AWS_REGION", "local"))
                    .addKeyValue("commit_hash", environment("GIT_COMMIT", "unknown"))
                    .addKeyValue("correlation_id", correlationId)
                    .addKeyValue("tenant_id", tenantId())
                    .addKeyValue("http_method", request.getMethod())
                    .addKeyValue("http_path", request.getRequestURI())
                    .addKeyValue("status_code", response.getStatus())
                    .addKeyValue("duration_ms", durationMs)
                    .addKeyValue("outcome", failure == null && response.getStatus() < 400 ? "success" : "error")
                    .addKeyValue("error_type", failure == null ? null : failure.getClass().getSimpleName())
                    .log("integration request completed");
        }
    }

    private String tenantId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String tenantId = jwt.getToken().getClaimAsString("tenant_id");
            return tenantId == null ? "unknown" : tenantId;
        }
        return "anonymous";
    }

    private String correlationId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9._:-]{1,128}")) return candidate;
        return UUID.randomUUID().toString();
    }

    private String version() {
        String version = IntegrationWideEventFilter.class.getPackage().getImplementationVersion();
        return version == null ? "development" : version;
    }

    private String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
