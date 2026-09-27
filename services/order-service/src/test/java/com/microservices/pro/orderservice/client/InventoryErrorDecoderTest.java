package com.microservices.pro.orderservice.client;

import com.microservices.pro.orderservice.exception.InsufficientStockException;
import com.microservices.pro.orderservice.exception.ProductNotFoundException;
import com.microservices.pro.orderservice.exception.ServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryErrorDecoderTest {

    private final InventoryErrorDecoder decoder = new InventoryErrorDecoder();

    @Test
    void conflict_shouldBecomeInsufficientStockException() {
        assertThat(decoder.decode("InventoryClient#checkStock", response(409)))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void notFound_shouldBecomeProductNotFoundException() {
        assertThat(decoder.decode("InventoryClient#checkStock", response(404)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void serviceUnavailable_shouldBecomeServiceUnavailableException() {
        assertThat(decoder.decode("InventoryClient#checkStock", response(503)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void otherErrors_shouldStayFeignExceptions() {
        assertThat(decoder.decode("InventoryClient#checkStock", response(500)))
                .isInstanceOf(FeignException.class);
    }

    private static Response response(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/inventory/check",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return Response.builder().status(status).request(request).headers(Map.of()).build();
    }
}
