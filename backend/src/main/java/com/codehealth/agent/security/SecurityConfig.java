package com.codehealth.agent.security;

import com.codehealth.agent.config.CodeHealthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: SecurityConfig.java
 *  Date: October 31, 2026
 * Two modes:
 * <ul>
 *   <li>demo: no login. Every caller is the principal "demo-user" with VIEWER and APPROVER roles,
 *       so the approval audit trail still records a named actor.</li>
 *   <li>keycloak: JWT bearer tokens; roles come from realm_access.roles.</li>
 * </ul>
 * The CI ingest endpoint is guarded separately by a shared token (see {@code CiIngestController}).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CodeHealthProperties codeHealthProperties;

    public SecurityConfig(CodeHealthProperties codeHealthProperties) {
        this.codeHealthProperties = codeHealthProperties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ci/runs").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("VIEWER", "APPROVER", "ADMIN", "AUDITOR")
                        // hasAnyRole (not authenticated()) so the demo-mode anonymous principal is accepted.
                        .anyRequest().hasAnyRole("APPROVER", "ADMIN"));

        if (codeHealthProperties.security().isKeycloak()) {
            http.oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(new KeycloakRoleConverter())));
        } else {
            http.anonymous(anonymous -> anonymous
                    .principal("demo-user")
                    .authorities(AuthorityUtils.createAuthorityList("ROLE_VIEWER", "ROLE_APPROVER", "ROLE_ADMIN")));
        }
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(codeHealthProperties.security().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID"));
        config.setExposedHeaders(List.of("X-Correlation-ID"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}