package com.microservices.pro.orderservice.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceTokenClientTest {

    private static final String TOKEN_PATH = "/realms/ecommerce-platform/protocol/openid-connect/token";

    private final WireMockServer keycloak = new WireMockServer(wireMockConfig().dynamicPort());
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-28T10:00:00Z"));
    private OrderServiceTokenClient tokenClient;

    @BeforeEach
    void setUp() {
        keycloak.start();
        keycloak.stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"service-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        tokenClient = new OrderServiceTokenClient(RestClient.builder(), keycloak.baseUrl() + TOKEN_PATH,
                "order-service", "secret", clock);
    }

    @AfterEach
    void tearDown() {
        keycloak.stop();
    }

    @Test
    void getAccessToken_shouldUseTheClientCredentialsGrant() {
        assertThat(tokenClient.getAccessToken()).isEqualTo("service-token");

        keycloak.verify(postRequestedFor(urlEqualTo(TOKEN_PATH))
                .withRequestBody(containing("grant_type=client_credentials"))
                .withRequestBody(containing("client_id=order-service")));
    }

    @Test
    void getAccessToken_shouldReuseTheTokenUntilItIsAboutToExpire() {
        tokenClient.getAccessToken();
        clock.advanceSeconds(200);
        tokenClient.getAccessToken();
        keycloak.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));

        clock.advanceSeconds(80);
        tokenClient.getAccessToken();

        keycloak.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
