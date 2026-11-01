package com.codehealth.agent.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 *  Author: Kumar.Kunal
 *  Project: codeguardian-ai
 *  File: KeycloakRoleConverter.java
 *  Date: October 31, 2026
 * Maps Keycloak realm roles to Spring authorities and uses preferred_username as the principal name,
 * so the approver recorded in the audit trail is a readable, verified identity.
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String name = jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, authorities(jwt), name != null ? name : jwt.getSubject());
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(
                        "ROLE_" + String.valueOf(role).toUpperCase(Locale.ROOT)))
                .toList();
    }
}
