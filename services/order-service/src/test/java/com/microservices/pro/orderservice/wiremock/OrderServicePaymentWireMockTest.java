package com.microservices.pro.orderservice.wiremock;

import com.microservices.pro.orderservice.client.InventoryClient;
import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.OrderResponse;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import com.microservices.pro.orderservice.exception.ServiceUnavailableException;
import com.microservices.pro.orderservice.service.OrderService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.kafka.listener.auto-startup=false")
class OrderServicePaymentWireMockTest {

    private static final WireMockServer wireMock = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
        configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void paymentUrl(DynamicPropertyRegistry registry) {
        registry.add("payment.service.url", () -> "http://localhost:" + wireMock.port() + "/api/payments");
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private InventoryClient inventoryClient;

    private void stubInventory() {
        when(inventoryClient.checkStock("PROD-001", 1))
                .thenReturn(new StockCheckResponse("PROD-001", 1, true, 99));
        when(inventoryClient.checkStock("PROD-002", 2))
                .thenReturn(new StockCheckResponse("PROD-002", 2, true, 3));
    }

    @Test
    void createOrder_returnsConfirmed_whenPaymentApproved() {
        stubInventory();

        stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"transactionId\":\"TXN-001\",\"status\":\"APPROVED\"}")));

        OrderResponse response = orderService.createOrderAsync(
                new OrderRequest("PROD-001", 1, 100.0)
        ).join();

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.message()).isEqualTo(
                "{\"transactionId\":\"TXN-001\",\"status\":\"APPROVED\"}");
    }

    @Test
    void createOrder_returnsPending_whenPaymentServiceUnavailable() {
        stubInventory();

        stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse().withStatus(503)));

        OrderResponse response = orderService.createOrderAsync(
                new OrderRequest("PROD-001", 1, 100.0)
        ).join();

        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void createOrder_sendsCorrectPayload_toPaymentService() {
        stubInventory();

        stubFor(post(urlEqualTo("/api/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("Payment successful")));

        orderService.createOrderAsync(
                new OrderRequest("PROD-002", 2, 250.0)
        ).join();

        verify(postRequestedFor(urlEqualTo("/api/payments"))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("250.0"))));
    }

    @Test
    void createOrder_returnsPending_whenInventoryServiceIsDown() {
        when(inventoryClient.checkStock("PROD-001", 1))
                .thenThrow(new ServiceUnavailableException("Inventory unavailable"));

        OrderResponse response = orderService.createOrderAsync(
                new OrderRequest("PROD-001", 1, 100.0)
        ).join();

        assertThat(response.status()).isEqualTo("PENDING");
        verify(0, postRequestedFor(urlEqualTo("/api/payments")));
    }
}
