package com.microservices.pro.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();

    @Test
    void ipKeyResolver_shouldUseClientIpAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .remoteAddress(new InetSocketAddress("10.0.0.7", 5555)));

        assertThat(config.ipKeyResolver().resolve(exchange).block()).isEqualTo("10.0.0.7");
    }

    @Test
    void userKeyResolver_shouldUseUserIdHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .header("X-User-Id", "user123"));

        assertThat(config.userKeyResolver().resolve(exchange).block()).isEqualTo("user123");
    }

    @Test
    void userKeyResolver_withoutUser_shouldFallBackToAnonymous() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));

        assertThat(config.userKeyResolver().resolve(exchange).block()).isEqualTo("anonymous");
    }
}
