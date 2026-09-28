package com.microservices.pro.apigateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Component
public class UserHeaderFilter implements GlobalFilter, Ordered {

    static final String USER_ID = "X-User-Id";
    static final String USER_ROLE = "X-User-Role";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange withoutClientHeaders = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove(USER_ID);
                            headers.remove(USER_ROLE);
                        })
                        .build())
                .build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(authentication -> withUserHeaders(withoutClientHeaders, authentication))
                .defaultIfEmpty(withoutClientHeaders)
                .flatMap(chain::filter);
    }

    private ServerWebExchange withUserHeaders(ServerWebExchange exchange, JwtAuthenticationToken authentication) {
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .collect(Collectors.joining(","));
        return exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.add(USER_ID, authentication.getToken().getSubject());
                            headers.add(USER_ROLE, roles);
                        })
                        .build())
                .build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
