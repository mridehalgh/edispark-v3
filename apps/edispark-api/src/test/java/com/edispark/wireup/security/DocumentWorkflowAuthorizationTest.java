package com.edispark.wireup.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(DocumentWorkflowAuthorizationTest.TestConfiguration.class)
class DocumentWorkflowAuthorizationTest {
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void configure() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test void membersCanValidateCorrectAndSubmitDocumentsWithoutConfigurationPrivileges() throws Exception {
        for (String path : new String[]{"/api/exchanges/readiness", "/api/exchanges", "/api/document-sets",
                "/api/document-sets/set/documents/doc/corrections", "/api/document-sets/set/documents/doc/related",
                "/api/document-sets/set/documents/doc/finalize", "/api/document-sets/set/documents/doc/versions/1/validate"}) {
            mvc.perform(post(path).header("Authorization", "Bearer member")).andExpect(status().isOk());
        }
        for (String path : new String[]{"/api/trading-partners", "/api/trading-partners/agreements", "/api/trading-partners/partner/document-profiles"}) {
            mvc.perform(get(path).header("Authorization", "Bearer member")).andExpect(status().isOk());
        }
    }

    @Test void mappingAndPartnerConfigurationRemainRestrictedToAdministrators() throws Exception {
        for (String path : new String[]{"/api/mappings/mapping/revisions", "/api/mappings/mapping/publish", "/api/mappings/mapping/test-retained-source", "/api/trading-partners"}) {
            mvc.perform(post(path).header("Authorization", "Bearer member")).andExpect(status().isForbidden());
            mvc.perform(post(path).header("Authorization", "Bearer admin")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/mappings/mapping").header("Authorization", "Bearer member")).andExpect(status().isForbidden());
    }

    @Test void readinessRequiresBothAMembershipAndAnAuthenticatedTenant() throws Exception {
        mvc.perform(post("/api/exchanges/readiness")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/exchanges/readiness").header("Authorization", "Bearer outsider")).andExpect(status().isForbidden());
        mvc.perform(post("/api/exchanges/readiness").header("Authorization", "Bearer no-tenant")).andExpect(status().isUnauthorized());
    }

    @Test void platformCatalogueAuthorizationIsIndependentFromOrganisationRoles() throws Exception {
        String platform = "/api/platform/managed-partners";
        mvc.perform(post(platform).header("Authorization", "Bearer platform")).andExpect(status().isOk());
        for (String role : new String[]{"owner", "admin", "member"}) {
            mvc.perform(post(platform).header("Authorization", "Bearer " + role)).andExpect(status().isForbidden());
        }
        mvc.perform(post(platform)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/managed-partners").header("Authorization", "Bearer platform")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/managed-partners").header("Authorization", "Bearer member")).andExpect(status().isForbidden());
        mvc.perform(get("/api/managed-partners").header("Authorization", "Bearer owner")).andExpect(status().isOk());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfiguration.class, Routes.class})
    static class TestConfiguration {
        @Bean JwtDecoder jwtDecoder() {
            return token -> {
                var jwt = Jwt.withTokenValue(token).header("alg", "test").subject("test-actor")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                        .claim("organization_role", token.equals("no-tenant") ? "member" : token);
                if (token.equals("platform")) return jwt.claim("platform_role", "platform_admin").build();
                if (!token.equals("no-tenant")) jwt.claim("tenant_id", "2OnHVzeAJKwm0ThFLcy1HfLHGPm");
                return jwt.build();
            };
        }
    }

    @RestController
    static class Routes {
        @RequestMapping("/api/**") String accepted() { return "accepted"; }
    }
}
