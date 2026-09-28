package com.microservices.pro.orderservice.controller;

import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.OrderResponse;
import com.microservices.pro.orderservice.dto.OrderStatusResponse;
import com.microservices.pro.orderservice.dto.SagaOrderResponse;
import com.microservices.pro.orderservice.saga.OrderSagaOrchestrator;
import com.microservices.pro.orderservice.service.OrderService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderSagaOrchestrator orderSagaOrchestrator;

    @Timed(value = "order.create.duration", description = "Time to create an order")
    @PostMapping
    public CompletableFuture<ResponseEntity<OrderResponse>> createOrder(
            @RequestBody OrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole
    ) {
        log.info("[ORDER] Order request from user {} with roles {}", userId, userRole);
        return orderService.createOrderAsync(request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/choreography")
    public ResponseEntity<SagaOrderResponse> createOrderWithChoreography(
            @RequestBody OrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String customerId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(orderService.createOrder(request, customerId));
    }

    @PostMapping("/saga")
    public ResponseEntity<SagaOrderResponse> createOrderWithOrchestration(
            @RequestBody OrderRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String customerId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(orderSagaOrchestrator.startSaga(request, customerId));
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable String orderId) {
        return orderService.getOrderStatus(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
