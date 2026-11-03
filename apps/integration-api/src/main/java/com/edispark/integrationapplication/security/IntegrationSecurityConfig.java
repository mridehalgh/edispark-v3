package com.edispark.integrationapplication.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class IntegrationSecurityConfig {
    @Bean SecurityFilterChain integrationSecurityFilterChain(HttpSecurity http) throws Exception {
        IntegrationTenantContextFilter tenantFilter = new IntegrationTenantContextFilter();
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api-docs", "/swagger-ui/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/exchanges/eligible-routes")
                            .hasAnyAuthority("ROLE_member", "ROLE_admin", "ROLE_owner")
                        .requestMatchers(HttpMethod.GET, "/api/exchanges")
                            .hasAnyAuthority("ROLE_member", "ROLE_admin", "ROLE_owner")
                        .requestMatchers(HttpMethod.POST, "/api/exchanges")
                            .hasAnyAuthority("ROLE_member", "ROLE_admin", "ROLE_owner")
                        .requestMatchers("/api/**").hasAnyAuthority("ROLE_admin", "ROLE_owner")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())))
                .addFilterAfter(tenantFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(new IntegrationWideEventFilter(), IntegrationTenantContextFilter.class).build();
    }

    private JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("organization_role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
