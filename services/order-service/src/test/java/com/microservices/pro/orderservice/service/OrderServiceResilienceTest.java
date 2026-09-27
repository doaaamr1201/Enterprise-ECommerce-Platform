package com.microservices.pro.orderservice.service;

import com.microservices.pro.orderservice.client.InventoryClient;
import com.microservices.pro.orderservice.client.PaymentClient;
import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.OrderResponse;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderServiceResilienceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoBean
    private InventoryClient inventoryClient;

    private final OrderRequest request = new OrderRequest("PROD-001", 1, 100.0);

    @BeforeEach
    void stockAvailable() {
        when(inventoryClient.checkStock(anyString(), anyInt()))
                .thenReturn(new StockCheckResponse("PROD-001", 1, true, 99));
    }

    @Test
    void slowPayment_shouldReturnPendingAfterTwoSeconds() {
        when(paymentClient.processPayment(any())).thenAnswer(invocation -> {
            Thread.sleep(3_000);
            return "Payment successful";
        });

        long start = System.nanoTime();
        OrderResponse response = orderService.createOrder(request).join();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.message()).isEqualTo("Payment is taking too long");
        assertThat(elapsed).isLessThan(Duration.ofMillis(2_900));
    }

    @Test
    void fifteenConcurrentOrders_shouldQueueTheOnesAboveTheBulkheadLimit() {
        when(paymentClient.processPayment(any())).thenAnswer(invocation -> {
            Thread.sleep(100);
            return "Payment successful";
        });
        int maxConcurrentCalls = bulkheadRegistry.bulkhead("paymentService")
                .getBulkheadConfig().getMaxConcurrentCalls();

        List<CompletableFuture<OrderResponse>> orders = IntStream.range(0, 15)
                .mapToObj(i -> orderService.createOrder(request))
                .toList();
        List<String> statuses = orders.stream().map(f -> f.join().status()).toList();

        assertThat(statuses).filteredOn("QUEUED"::equals).hasSize(15 - maxConcurrentCalls);
        assertThat(statuses).filteredOn("CONFIRMED"::equals).hasSize(maxConcurrentCalls);
    }

    @Test
    void bulkheadFallback_shouldReturnQueued() {
        OrderResponse response = orderService.bulkheadFallback(request,
                BulkheadFullException.createBulkheadFullException(
                        bulkheadRegistry.bulkhead("paymentService"))).join();

        assertThat(response.status()).isEqualTo("QUEUED");
    }

    @Test
    void timeoutFallback_shouldReturnPending() {
        OrderResponse response = orderService.timeoutFallback(request, new TimeoutException("2s")).join();

        assertThat(response.status()).isEqualTo("PENDING");
    }
}
