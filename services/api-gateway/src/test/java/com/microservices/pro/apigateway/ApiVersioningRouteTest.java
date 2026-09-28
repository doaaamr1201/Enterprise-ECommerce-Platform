package com.microservices.pro.apigateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false"
})
class ApiVersioningRouteTest {

    private static final WireMockServer productService = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        productService.start();
    }

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @DynamicPropertySource
    static void productServiceInstance(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.PRODUCT-SERVICE[0].uri", productService::baseUrl);
    }

    @AfterAll
    static void stopProductService() {
        productService.stop();
    }

    @BeforeEach
    void setUp() {
        productService.resetAll();
        productService.stubFor(get(urlPathMatching("/api/v1/products.*"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[]")));
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).responseTimeout(Duration.ofSeconds(30)).build();
    }

    @Test
    void legacyPath_shouldBeRewrittenToV1AndMarkedDeprecated() {
        client.get().uri("/api/products/5").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Deprecation", "true")
                .expectHeader().valueEquals("Sunset", "Thu, 31 Dec 2026 00:00:00 GMT");

        productService.verify(getRequestedFor(urlEqualTo("/api/v1/products/5")));
    }

    @Test
    void legacyCollectionPath_shouldBeRewrittenToTheV1Collection() {
        client.get().uri("/api/products").exchange().expectStatus().isOk();

        productService.verify(getRequestedFor(urlEqualTo("/api/v1/products")));
    }

    @Test
    void versionedPath_shouldBeForwardedWithoutDeprecationHeaders() {
        client.get().uri("/api/v1/products/5").exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Deprecation")
                .expectHeader().valueEquals("X-Platform", "microservices-pro");

        productService.verify(getRequestedFor(urlEqualTo("/api/v1/products/5")));
    }
}
