package com.microservices.pro.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    @Test
    void realmRoles_shouldBecomeSpringRoles() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("ADMIN", "CUSTOMER"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CUSTOMER");
    }

    @Test
    void tokenWithoutRealmRoles_shouldHaveNoAuthorities() {
        assertThat(converter.convert(jwt(Map.of("scope", "openid")))).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("user-1").claims(c -> c.putAll(claims)).build();
    }
}
