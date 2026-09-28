package com.microservices.pro.orderservice.service;

import com.microservices.pro.orderservice.client.InventoryClient;
import com.microservices.pro.orderservice.client.PaymentClient;
import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.OrderResponse;
import com.microservices.pro.orderservice.dto.SagaOrderResponse;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import com.microservices.pro.orderservice.event.OrderPlacedEvent;
import com.microservices.pro.orderservice.event.SagaTopics;
import com.microservices.pro.orderservice.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.microservices.pro.orderservice.model.OrderStatus;
import com.microservices.pro.orderservice.repository.OrderRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Spy
    private OrderRepository orderRepository = new OrderRepository();

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_shouldSavePendingOrderAndPublishOrderPlaced() {
        OrderRequest request = new OrderRequest("PROD-001", 3, 250.0);

        SagaOrderResponse response = orderService.createOrder(request, "user123");

        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals(OrderStatus.PENDING, orderRepository.findById(response.orderId()).orElseThrow().getStatus());
        verify(kafkaTemplate).send(SagaTopics.ORDER_EVENTS, response.orderId(), new OrderPlacedEvent(
                response.orderId(), "PROD-001", 3, BigDecimal.valueOf(250.0), "user123"));
        verifyNoInteractions(inventoryClient, paymentClient);
    }

    @Test
    void shouldReturnConfirmedWhenPaymentSucceeds() {
        OrderRequest request = new OrderRequest("PROD-001", 1, 250.0);
        when(inventoryClient.checkStock("PROD-001", 1))
                .thenReturn(new StockCheckResponse("PROD-001", 1, true, 99));
        when(paymentClient.processPayment(request)).thenReturn("Payment successful");

        OrderResponse response = orderService.createOrderAsync(request).join();

        assertEquals("CONFIRMED", response.status());
        assertEquals("Payment successful", response.message());
    }

    @Test
    void shouldRejectOrderWithoutPayingWhenInventoryReportsNoStock() {
        OrderRequest request = new OrderRequest("PROD-003", 1, 250.0);
        when(inventoryClient.checkStock("PROD-003", 1))
                .thenThrow(new InsufficientStockException("Product out of stock"));

        OrderResponse response = orderService.createOrderAsync(request).join();

        assertEquals("REJECTED", response.status());
        verifyNoInteractions(paymentClient);
    }

    @Test
    void inventoryCallShouldSeeTheIncomingRequestSoTheJwtCanBeForwarded() {
        OrderRequest request = new OrderRequest("PROD-001", 1, 250.0);
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader("Authorization", "Bearer abc.def.ghi");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
        AtomicReference<String> seenByInventoryCall = new AtomicReference<>();
        when(inventoryClient.checkStock("PROD-001", 1)).thenAnswer(invocation -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            seenByInventoryCall.set(attrs.getRequest().getHeader("Authorization"));
            return new StockCheckResponse("PROD-001", 1, true, 99);
        });
        when(paymentClient.processPayment(request)).thenReturn("Payment successful");

        try {
            orderService.createOrderAsync(request).join();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertEquals("Bearer abc.def.ghi", seenByInventoryCall.get());
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
