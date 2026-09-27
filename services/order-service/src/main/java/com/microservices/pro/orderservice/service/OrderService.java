package com.microservices.pro.orderservice.service;

import com.microservices.pro.orderservice.client.InventoryClient;
import com.microservices.pro.orderservice.client.PaymentClient;
import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.OrderResponse;
import com.microservices.pro.orderservice.dto.StockCheckResponse;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;

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
    public CompletableFuture<OrderResponse> createOrder(OrderRequest request) {

        return CompletableFuture.supplyAsync(() -> {

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

            } catch (FeignException.Conflict ex) {

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
        });
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
            Throwable ex
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
            Throwable ex
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