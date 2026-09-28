package com.microservices.pro.apigateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserHeaderFilterTest {

    private final UserHeaderFilter filter = new UserHeaderFilter();
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void authenticatedRequest_shouldCarryUserHeadersFromTheJwt() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("keycloak-user-id").build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("ROLE_ADMIN")));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/orders")
                .header(UserHeaderFilter.USER_ID, "spoofed"));

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block();

        HttpHeaders headers = forwardedHeaders();
        assertThat(headers.get(UserHeaderFilter.USER_ID)).containsExactly("keycloak-user-id");
        assertThat(headers.getFirst(UserHeaderFilter.USER_ROLE)).isEqualTo("CUSTOMER,ADMIN");
    }

    @Test
    void anonymousRequest_shouldHaveClientSuppliedUserHeadersRemoved() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .header(UserHeaderFilter.USER_ID, "spoofed")
                .header(UserHeaderFilter.USER_ROLE, "ADMIN"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwardedHeaders();
        assertThat(headers.containsHeader(UserHeaderFilter.USER_ID)).isFalse();
        assertThat(headers.containsHeader(UserHeaderFilter.USER_ROLE)).isFalse();
    }

    private HttpHeaders forwardedHeaders() {
        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        return forwarded.getValue().getRequest().getHeaders();
    }
}
