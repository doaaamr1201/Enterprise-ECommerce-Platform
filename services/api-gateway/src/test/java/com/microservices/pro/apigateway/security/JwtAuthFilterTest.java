package com.microservices.pro.apigateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "microservices-pro-course-secret-key-2024-minimum-256-bits";

    private JwtAuthFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(new JwtUtil(SECRET));
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void publicGetRoute_shouldPassWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void protectedRoute_withoutToken_shouldReturn401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/products"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedRoute_withInvalidToken_shouldReturn401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedRoute_withTokenWithoutRole_shouldReturn401() {
        String token = token(SECRET, null, 3_600_000);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRoute_withExpiredToken_shouldReturn401() {
        String token = token(SECRET, "CUSTOMER", -60_000);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRoute_withValidToken_shouldForwardWithUserHeaders() {
        String token = token(SECRET, "ADMIN", 3_600_000);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "spoofed"));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        HttpHeaders headers = forwarded.getValue().getRequest().getHeaders();
        assertThat(headers.get("X-User-Id")).containsExactly("user123");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    private static String token(String secret, String role, long ttlMillis) {
        var builder = Jwts.builder()
                .setSubject("user123")
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }
}
