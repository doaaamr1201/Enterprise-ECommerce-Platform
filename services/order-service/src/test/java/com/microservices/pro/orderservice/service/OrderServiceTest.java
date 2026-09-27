package com.microservices.pro.orderservice.service;

import com.microservices.pro.orderservice.client.InventoryClient;
import com.microservices.pro.orderservice.client.PaymentClient;
import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.OrderResponse;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldReturnConfirmedWhenPaymentSucceeds() {
        OrderRequest request = new OrderRequest("PROD-001", 1, 250.0);
        when(inventoryClient.checkStock("PROD-001", 1))
                .thenReturn(new StockCheckResponse("PROD-001", 1, true, 99));
        when(paymentClient.processPayment(request)).thenReturn("Payment successful");

        OrderResponse response = orderService.createOrder(request).join();

        assertEquals("CONFIRMED", response.status());
        assertEquals("Payment successful", response.message());
    }

    @Test
    void shouldReturnPendingWhenPaymentFails() {
        OrderRequest request = new OrderRequest("PROD-001", 1, 250.0);

        OrderResponse response = orderService.paymentFallback(
                request, new RuntimeException("Payment Service unavailable")).join();

        assertEquals("PENDING", response.status());
        assertEquals("Will retry payment later", response.message());
    }

    @Test
    void fallbackShouldIncludeRetryMessage() {
        OrderRequest request = new OrderRequest("PROD-001", 1, 100.0);

        OrderResponse response = orderService.paymentFallback(
                request, new RuntimeException("boom")).join();

        assertEquals("PENDING", response.status());
        assertEquals("Will retry payment later", response.message());
    }
}
