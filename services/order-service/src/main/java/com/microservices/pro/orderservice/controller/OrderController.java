package com.microservices.pro.orderservice.controller;

import com.microservices.pro.orderservice.dto.OrderRequest;
import com.microservices.pro.orderservice.dto.OrderResponse;
import com.microservices.pro.orderservice.dto.OrderStatusResponse;
import com.microservices.pro.orderservice.dto.SagaOrderResponse;
import com.microservices.pro.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public CompletableFuture<ResponseEntity<OrderResponse>> createOrder(
            @RequestBody OrderRequest request
    ) {
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

    @GetMapping("/{orderId}/status")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable String orderId) {
        return orderService.getOrderStatus(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
