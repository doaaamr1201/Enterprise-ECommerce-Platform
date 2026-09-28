package com.microservices.pro.orderservice.service;

import com.microservices.pro.orderservice.client.InventoryClient;
import com.microservices.pro.orderservice.client.PaymentClient;
import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.OrderResponse;
import com.microservices.pro.orderservice.dto.OrderStatusResponse;
import com.microservices.pro.orderservice.dto.SagaOrderResponse;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import com.microservices.pro.orderservice.event.OrderPlacedEvent;
import com.microservices.pro.orderservice.event.SagaTopics;
import com.microservices.pro.orderservice.exception.InsufficientStockException;
import com.microservices.pro.orderservice.model.Order;
import com.microservices.pro.orderservice.model.OrderStatus;
import com.microservices.pro.orderservice.repository.OrderRepository;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderRepository orderRepository;

    public SagaOrderResponse createOrder(OrderRequest request, String customerId) {
        Order order = new Order(
                UUID.randomUUID().toString(),
                request.productId(),
                request.quantity(),
                request.amount() == null ? null : BigDecimal.valueOf(request.amount()),
                customerId,
                OrderStatus.PENDING
        );
        orderRepository.save(order);

        kafkaTemplate.send(SagaTopics.ORDER_EVENTS, order.getOrderId(), new OrderPlacedEvent(
                order.getOrderId(),
                order.getProductId(),
                order.getQuantity(),
                order.getAmount(),
                customerId
        ));
        log.info("[SAGA] Order {} PENDING -- OrderPlaced published", order.getOrderId());

        return new SagaOrderResponse(order.getOrderId(), OrderStatus.PENDING, "Order received -- processing...");
    }

    public Optional<OrderStatusResponse> getOrderStatus(String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> new OrderStatusResponse(order.getOrderId(), order.getStatus()));
    }

    @Bulkhead(
            name = "paymentService",
            fallbackMethod = "bulkheadFallback"
    )
    @TimeLimiter(
            name = "paymentService",
            fallbackMethod = "timeoutFallback"
    )
    @CircuitBreaker(
            name = "paymentService",
            fallbackMethod = "paymentFallback"
    )
    @Retry(name = "paymentService")
    public CompletableFuture<OrderResponse> createOrderAsync(OrderRequest request) {

        return CompletableFuture.supplyAsync(() -> placeOrder(request));
    }

    private OrderResponse placeOrder(OrderRequest request) {

        // 1. Check inventory
        try {
            StockCheckResponse stock = inventoryClient.checkStock(
                    request.productId(),
                    request.quantity()
            );

            if (!stock.available()) {
                log.warn(
                        "[ORDER] Insufficient stock for product: {}",
                        request.productId()
                );

                return new OrderResponse(
                        "REJECTED",
                        "Insufficient stock"
                );
            }

        } catch (InsufficientStockException ex) {

            log.warn(
                    "[ORDER] Inventory unavailable for product: {}. Remaining stock: 0",
                    request.productId()
            );

            return new OrderResponse(
                    "REJECTED",
                    "Insufficient stock"
            );
        }

        // 2. Process payment only when stock is available
        String result = paymentClient.processPayment(request);

        log.info("[ORDER] Payment succeeded: {}", result);

        // 3. Confirm order
        return new OrderResponse(
                "CONFIRMED",
                result
        );
    }

    public CompletableFuture<OrderResponse> paymentFallback(
            OrderRequest request,
            Throwable ex
    ) {
        log.warn(
                "[FALLBACK] Payment failed -> PENDING. Reason: {}",
                ex.getMessage()
        );

        return CompletableFuture.completedFuture(
                new OrderResponse(
                        "PENDING",
                        "Will retry payment later"
                )
        );
    }

    public CompletableFuture<OrderResponse> bulkheadFallback(
            OrderRequest request,
            BulkheadFullException ex
    ) {
        log.warn(
                "[BULKHEAD] Concurrent limit reached. Reason: {}",
                ex.getMessage()
        );

        return CompletableFuture.completedFuture(
                new OrderResponse(
                        "QUEUED",
                        "System busy — your order is queued"
                )
        );
    }

    public CompletableFuture<OrderResponse> timeoutFallback(
            OrderRequest request,
            TimeoutException ex
    ) {
        log.warn(
                "[TIMEOUT] Payment took too long. Reason: {}",
                ex.getMessage()
        );

        return CompletableFuture.completedFuture(
                new OrderResponse(
                        "PENDING",
                        "Payment is taking too long"
                )
        );
    }
}