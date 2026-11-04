package com.edispark.wireup.security;

import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import io.cottn.core.tenancy.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

final class UnifiedTenantContextFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A tenant claim is required");
            return;
        }
        String claim = authentication.getToken().getClaimAsString("tenant_id");
        if (claim == null || claim.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The tenant claim is invalid");
            return;
        }
        try {
            TenantContext.runWhere(TenantContextHolder.of(TenantId.fromString(claim)), () -> invoke(chain, request, response));
        } catch (IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The tenant claim is invalid");
        } catch (FilterFailure failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            throw (ServletException) failure.getCause();
        }
    }

    private void invoke(FilterChain chain, HttpServletRequest request, HttpServletResponse response) {
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException exception) {
            throw new FilterFailure(exception);
        }
    }

    private static final class FilterFailure extends RuntimeException {
        private FilterFailure(Exception cause) {
            super(cause);
        }
    }
}
