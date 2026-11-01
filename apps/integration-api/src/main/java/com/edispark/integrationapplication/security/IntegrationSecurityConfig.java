package com.edispark.integrationapplication.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class IntegrationSecurityConfig {
    @Bean SecurityFilterChain integrationSecurityFilterChain(HttpSecurity http) throws Exception {
        IntegrationTenantContextFilter tenantFilter = new IntegrationTenantContextFilter();
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api-docs", "/swagger-ui/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated().anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterAfter(tenantFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(new IntegrationWideEventFilter(), IntegrationTenantContextFilter.class).build();
    }
}
