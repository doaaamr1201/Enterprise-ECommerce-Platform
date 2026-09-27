package com.microservices.pro.orderservice.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.PactDslWithProvider;
import au.com.dius.pact.consumer.dsl.LambdaDsl;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import com.microservices.pro.orderservice.client.InventoryClient;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import feign.Contract;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "inventory-service", port = "8888")
class OrderServiceInventoryContractTest {

    @au.com.dius.pact.consumer.junit5.Pact(consumer = "order-service", provider = "inventory-service")
    RequestResponsePact checkStockAvailable(PactDslWithProvider builder) {
        return builder
                .given("PROD-001 has 100 units in stock")
                .uponReceiving("a stock check for PROD-001 quantity 5")
                .path("/api/v1/inventory/check")
                .method("GET")
                .query("productId=PROD-001&quantity=5")
                .willRespondWith()
                .status(200)
                .body(LambdaDsl.newJsonBody(body -> body
                        .stringType("productId", "PROD-001")
                        .integerType("requestedQuantity", 5)
                        .booleanValue("available", true)
                        .integerType("remainingStock", 95)
                ).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "checkStockAvailable")
    void checkStock_deserializesContractResponse_correctly(MockServer mockServer) {
        InventoryClient client = buildFeignClient(mockServer.getUrl());

        StockCheckResponse response = client.checkStock("PROD-001", 5);

        assertThat(response.productId()).isEqualTo("PROD-001");
        assertThat(response.requestedQuantity()).isEqualTo(5);
        assertThat(response.available()).isTrue();
        assertThat(response.remainingStock()).isGreaterThan(0);
    }

    private InventoryClient buildFeignClient(String baseUrl) {
        Contract contract = new SpringMvcContract();
        return Feign.builder()
                .contract(contract)
                .decoder(new JacksonDecoder())
                .target(InventoryClient.class, baseUrl);
    }
}
