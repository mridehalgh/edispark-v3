package com.edispark.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.TenantContext;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantContextFilterTest {

    private static final String TENANT_ID = "2OnHVzeAJKwm0ThFLcy1HfLHGPm";

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsApiRequestsWithoutAnAuthenticatedTenant() throws Exception {
        MockHttpServletResponse response = filterRequest("/api/document-sets");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsApiRequestsWithAnInvalidTenantClaim() throws Exception {
        authenticate("not-a-tenant-id");

        MockHttpServletResponse response = filterRequest("/api/document-sets");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void bindsTheAuthenticatedTenantOnlyForTheRequest() throws Exception {
        authenticate(TENANT_ID);
        AtomicReference<TenantId> observedTenant = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/document-sets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                observedTenant.set(TenantContext.getCurrentTenant()));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(observedTenant.get().asString()).isEqualTo(TENANT_ID);
    }

    private MockHttpServletResponse filterRequest(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });
        return response;
    }

    private void authenticate(String tenantId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "ES256")
                .subject("test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("tenant_id", tenantId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
