package com.edispark.wireup.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain edisparkSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api-docs", "/api-docs/**", "/swagger-ui/**", "/actuator/health",
                                "/as2/**", "/internal/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/document-sets/**", "/api/schemas/**",
                                "/api/exchanges/**").hasAnyAuthority("ROLE_member", "ROLE_admin", "ROLE_owner")
                        .requestMatchers(HttpMethod.POST, "/api/mft/transfers",
                                "/api/document-sets/*/documents/*/external-derivatives")
                                .hasAnyAuthority("ROLE_member", "ROLE_admin", "ROLE_owner")
                        .requestMatchers("/api/**").hasAnyAuthority("ROLE_admin", "ROLE_owner")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())))
                .addFilterAfter(new UnifiedTenantContextFilter(), AnonymousAuthenticationFilter.class)
                .build();
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
