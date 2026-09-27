package com.microservices.pro.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingFilterTest {

    private LoggingFilter loggingFilter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        loggingFilter = new LoggingFilter();
        chain = mock(GatewayFilterChain.class);
    }

    @Test
    void filter_shouldPassRequestToNextFilterInChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        loggingFilter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void getOrder_shouldRunBeforeEveryOtherFilter() {
        assertThat(loggingFilter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
