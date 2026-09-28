package com.microservices.pro.apigateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.gateway.server.webflux.filter.request-rate-limiter.deny-empty-key=false"
})
class GatewaySecurityTest {

    @Autowired
    private ApplicationContext context;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @Test
    void publicProductRead_shouldPassSecurityWithoutAToken() {
        int status = client.get().uri("/api/products").exchange().returnResult(Void.class).getStatus().value();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void protectedRoute_withoutToken_shouldReturn401() {
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void adminOnlyRoute_withCustomerToken_shouldReturn403() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .post().uri("/api/products").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminOnlyRoute_withAdminToken_shouldPassSecurity() {
        int status = client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .post().uri("/api/products").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().returnResult(Void.class).getStatus().value();

        assertThat(status).isNotIn(401, 403);
    }
}
