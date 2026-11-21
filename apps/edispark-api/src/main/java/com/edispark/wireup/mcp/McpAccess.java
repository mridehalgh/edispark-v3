package com.edispark.wireup.mcp;

import com.edispark.identifiers.tenant.TenantId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

final class McpAccess extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(McpAccess.class);
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(5);
    private final URI lookup;
    private final String secret;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CHECK_TIMEOUT).build();
    private final java.util.concurrent.Semaphore checks = new java.util.concurrent.Semaphore(16);

    McpAccess(URI issuer, String secret, ObjectMapper json) {
        this.lookup = issuer.resolve("/api/mcp/access");
        this.secret = secret;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (!"/mcp".equals(request.getServletPath()) && !"/mcp".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            chain.doFilter(request, response);
            return;
        }
        var token = authentication.getToken();
        String client = token.getClaimAsString("client_id");
        String organization = token.getClaimAsString("mcp_organization");
        var tenant = TenantId.fromString(token.getClaimAsString("tenant_id"));
        var scopes = Arrays.stream(token.getClaimAsString("scope").split(" "))
                .filter(scope -> scope.equals(McpTools.READ) || scope.equals(McpTools.PREVIEW)).collect(Collectors.toUnmodifiableSet());
        var principal = new Principal(tenant, token.getSubject(), client, scopes, UUID.randomUUID());
        response.setHeader("X-Correlation-Id", principal.correlation().toString());
        if (!checks.tryAcquire()) {
            response.setStatus(429);
            return;
        }
        int status;
        try {
            status = verify(principal, organization);
        } finally {
            checks.release();
        }
        if (status != 204) {
            log.info("mcp_access outcome={} tenant={} actor={} client={} correlationId={}",
                    status, tenant, principal.subject(), client, principal.correlation());
            response.setStatus(status);
            return;
        }
        request.setAttribute(McpTools.PRINCIPAL, principal);
        chain.doFilter(request, response);
    }

    private int verify(Principal principal, String organization) {
        try {
            var request = HttpRequest.newBuilder(lookup).timeout(CHECK_TIMEOUT)
                    .header("Authorization", "Bearer " + secret).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(McpEvidence.fields(
                            "tenantId", principal.tenant().asString(), "subject", principal.subject(),
                            "clientId", principal.client(), "organizationId", organization, "scopes", principal.scopes())))).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status == 204 || status == 403 ? status : 503;
        } catch (IOException exception) {
            log.debug("mcp_access_check dependencyFailure={}", exception.getClass().getSimpleName());
            return 503;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 503;
        }
    }

    record Principal(TenantId tenant, String subject, String client, Set<String> scopes, UUID correlation) {}
}
