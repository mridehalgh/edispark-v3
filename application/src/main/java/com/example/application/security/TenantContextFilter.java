package com.example.application.security;

import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Binds only the tenant asserted by an authenticated token to the request. */
final class TenantContextFilter extends OncePerRequestFilter {

    private final String localTenantId;

    TenantContextFilter(String localTenantId) {
        this.localTenantId = localTenantId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = resolveTenantId();
        if (tenantId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A tenant claim is required");
            return;
        }
        TenantId parsedTenantId;
        try {
            parsedTenantId = TenantId.fromString(tenantId);
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The tenant claim is invalid");
            return;
        }

        TenantContext.runWhere(TenantContextHolder.of(parsedTenantId), () -> {
            try {
                filterChain.doFilter(request, response);
            } catch (IOException | ServletException exception) {
                throw new TenantFilterException(exception);
            }
        });
    }

    private String resolveTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            return jwt.getClaimAsString("tenant_id");
        }
        return localTenantId;
    }

    private static final class TenantFilterException extends RuntimeException {
        private TenantFilterException(Exception cause) {
            super(cause);
        }
    }
}
