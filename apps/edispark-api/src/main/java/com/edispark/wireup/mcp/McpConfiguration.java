package com.edispark.wireup.mcp;

import com.edispark.identifiers.tenant.TenantId;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.feature.ToolsFeature;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.mcp.customizer.McpServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "edispark.mcp.enabled", havingValue = "true")
class McpConfiguration {
    private static final String METADATA_PATH = "/.well-known/oauth-protected-resource/mcp";
    private final URI resource;
    private final URI issuer;
    private final String jwks;
    private final String secret;

    McpConfiguration(@Value("${edispark.mcp.resource}") URI resource, @Value("${edispark.mcp.issuer}") URI issuer,
                     @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks,
                     @Value("${edispark.mcp.access-secret}") String secret) {
        if (!secureUri(resource) || !"/mcp".equals(resource.getPath()) || !secureUri(issuer)
                || secret.length() < 32 || secret.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("MCP requires an explicit resource, trusted issuer and access secret");
        }
        this.resource = resource;
        this.issuer = issuer;
        this.jwks = jwks;
        this.secret = secret;
    }

    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.ES256)
                .validateType(false).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(10)),
                new JwtIssuerValidator(issuer.toString()), this::validateAccessToken));
        return http.securityMatcher("/mcp", METADATA_PATH).csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes.requestMatchers(METADATA_PATH).permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder))
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(builder ->
                                builder.resource(resource.toString()).authorizationServer(issuer.toString())
                                        .resourceName("EdiSpark EDI").scope(McpTools.READ).scope(McpTools.PREVIEW)
                                        .tlsClientCertificateBoundAccessTokens(false)))
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setHeader("Cache-Control", "no-store");
                            response.setHeader("WWW-Authenticate", "Bearer resource_metadata=\""
                                    + resource.resolve(METADATA_PATH) + "\", scope=\"" + McpTools.READ + "\""
                                    + (request.getHeader("Authorization") == null ? "" : ", error=\"invalid_token\""));
                            response.setStatus(401);
                        }))
                .addFilterAfter(new McpAccess(issuer, secret, json), BearerTokenAuthenticationFilter.class).build();
    }

    OAuth2TokenValidatorResult validateAccessToken(Jwt token) {
        try {
            String scope = token.getClaimAsString("scope");
            if (List.of(resource.toString()).equals(token.getAudience())
                    && token.getIssuedAt() != null && token.getExpiresAt() != null
                    && token.getExpiresAt().isAfter(token.getIssuedAt())
                    && Duration.between(token.getIssuedAt(), token.getExpiresAt()).compareTo(Duration.ofMinutes(5)) <= 0
                    && !token.getIssuedAt().isAfter(java.time.Instant.now().plusSeconds(10))
                    && Boolean.TRUE.equals(token.getClaimAsBoolean("mcp_delegated"))
                    && identity(token.getSubject()) && identity(token.getClaimAsString("client_id"))
                    && identity(token.getClaimAsString("mcp_organization")) && scope != null && scope.length() <= 256
                    && Arrays.stream(scope.split(" ")).anyMatch(value -> value.equals(McpTools.READ) || value.equals(McpTools.PREVIEW))) {
                TenantId.fromString(token.getClaimAsString("tenant_id"));
                return OAuth2TokenValidatorResult.success();
            }
        } catch (RuntimeException ignored) {
            // Reject malformed claims without disclosing their contents.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }

    @Bean
    McpTools mcpTools(McpEvidence evidence, ObjectMapper json, MeterRegistry registry) {
        return new McpTools(evidence, new McpCursor(secret), json, registry);
    }

    @Bean
    McpServerCustomizer<McpServer.Builder> mcpToolFeature(McpTools tools) {
        return builder -> builder.feature(ToolsFeature.ofSync(tools.repository(), McpJsonDefaults.getMapper(),
                schemaValidator(), 0, McpSchema.CacheScope.PRIVATE));
    }

    @Bean
    McpTransportContextExtractor<ServerRequest> mcpTransportContext() {
        return request -> request.attribute(McpTools.PRINCIPAL)
                .filter(McpAccess.Principal.class::isInstance)
                .map(principal -> McpTransportContext.create(Map.of(McpTools.PRINCIPAL, principal)))
                .orElse(McpTransportContext.EMPTY);
    }

    private static JsonSchemaValidator schemaValidator() {
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        return (schema, content) -> {
            try {
                var input = content instanceof String text ? json.readTree(text) : json.valueToTree(content);
                var validator = factory.getSchema(json.valueToTree(schema));
                return validator.validate(input).isEmpty()
                        ? JsonSchemaValidator.ValidationResponse.asValid(input.toString())
                        : JsonSchemaValidator.ValidationResponse.asInvalid("Tool input or output does not match its schema");
            } catch (IOException | IllegalArgumentException exception) {
                return JsonSchemaValidator.ValidationResponse.asInvalid("Tool input or output is invalid JSON");
            }
        };
    }

    private static boolean identity(String value) {
        return value != null && value.matches("[A-Za-z0-9_:.~-]{1,128}");
    }

    private static boolean secureUri(URI uri) {
        return uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                        && List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()));
    }
}
